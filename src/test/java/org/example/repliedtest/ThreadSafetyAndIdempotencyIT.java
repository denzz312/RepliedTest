package org.example.repliedtest;

import org.example.repliedtest.follow.FollowService;
import org.example.repliedtest.user.BlockService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Testcontainers
@SpringBootTest(classes = RepliedTestApplication.class)
@TestMethodOrder(MethodOrderer.DisplayName.class)
@ResourceLock("neo4j")
class ThreadSafetyAndIdempotencyIT {

    private static final Logger log = LoggerFactory.getLogger(ThreadSafetyAndIdempotencyIT.class);

    @Container
    static final Neo4jContainer<?> neo4j =
            new Neo4jContainer<>("neo4j:5.23")
                    .withEnv("NEO4J_AUTH", "neo4j/password")
                    .withLogConsumer(new Slf4jLogConsumer(log).withSeparateOutputStreams())
                    .waitingFor(Wait.forLogMessage(".*Bolt enabled on.*", 1))
                    .withStartupTimeout(Duration.ofMinutes(3));

    @DynamicPropertySource
    static void neoProps(DynamicPropertyRegistry r) {
        r.add("spring.neo4j.uri", neo4j::getBoltUrl);
        r.add("spring.neo4j.authentication.username", () -> "neo4j");
        r.add("spring.neo4j.authentication.password", () -> "password");
        // Don’t run demo seeders in tests
        r.add("app.seed.demodata", () -> "false");
    }

    @BeforeAll
    static void ensureDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available – skipping ThreadSafetyAndIdempotencyIT");
    }

    @Autowired
    Neo4jClient neo;

    @Autowired
    FollowService followRequestService; // sendFollowRequest / acceptFollowRequest / denyFollowRequest

    @Autowired
    BlockService blockService;

    @BeforeEach
    void cleanAndSeed() {
        // wipe graph
        neo.query("MATCH (n) DETACH DELETE n").run();
        // minimal constraints
        neo.query("CREATE CONSTRAINT user_id IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE").run();
        neo.query("CREATE CONSTRAINT user_username IF NOT EXISTS FOR (u:User) REQUIRE u.username IS UNIQUE").run();

        neo.query("""
            MERGE (a:__Seed {k:1})
            MERGE (b:__Seed {k:2})
            MERGE (a)-[r1:FOLLOWS]->(b)
            MERGE (a)-[r2:BLOCKS]->(b)
            MERGE (a)-[r3:FOLLOW_REQUEST]->(b)
            DELETE r1, r2, r3
            DETACH DELETE a, b
        """).run();

        // create two users A and B used by most tests
        neo.query("""
            MERGE (:User {id:'A', username:'alice', birthdate:'1990-01-01'})
            MERGE (:User {id:'B', username:'bob',   birthdate:'1990-02-02'})
            """).run();

        // quick ping so first real query doesn't stall
        neo.query("RETURN 1 AS x").fetch().one();
    }

    // some helpers
    private void runFlood(int threads, Runnable action) throws Exception {
        int poolSize = Math.min(Math.max(threads, 2), 12);
        ExecutorService ex = Executors.newFixedThreadPool(poolSize);
        CountDownLatch start = new CountDownLatch(1);
        List<? extends Future<?>> futures = IntStream.range(0, threads)
                .mapToObj(i -> ex.submit(() -> {
                    try {
                        if (!start.await(5, TimeUnit.SECONDS)) return;
                        action.run();
                    } catch (InterruptedException ignore) {
                        Thread.currentThread().interrupt();
                    } catch (Throwable t) {
                        log.debug("Worker error (ignored for idempotency): {}", t.toString());
                    }
                }))
                .toList();

        start.countDown();

        for (Future<?> f : futures) {
            try {
                f.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                log.warn("Worker future timed out; continuing (final graph assertions will tell)");
            } catch (ExecutionException ee) {
                log.debug("Worker failed: {}", ee.toString());
            }
        }

        ex.shutdown();
        if (!ex.awaitTermination(30, TimeUnit.SECONDS)) {
            ex.shutdownNow();
            // don’t hang the build
            throw new IllegalStateException("Executor did not terminate cleanly");
        }
    }

    private long relCount(String fromId, String type, String toId) {
        String cypher = "MATCH (:User {id:$from})-[r:" + type + "]->(:User {id:$to}) RETURN count(r) AS c";
        return neo.query(cypher).bind(fromId).to("from").bind(toId).to("to")
                .fetchAs(Long.class).one().orElse(0L);
    }

    private long relCountEitherDirection(String id1, String type, String id2) {
        String cypher = "MATCH (a:User {id:$a})-[r:" + type + "]-(b:User {id:$b}) RETURN count(r) AS c";
        return neo.query(cypher).bind(id1).to("a").bind(id2).to("b")
                .fetchAs(Long.class).one().orElse(0L);
    }

    // ---------- tests

    @Test
    @Timeout(60)
    @DisplayName("01) Concurrent send follow request -> exactly ONE FOLLOW_REQUEST")
    void sendRequest_concurrent_singlePending() throws Exception {
        int N = 50;

        runFlood(N, () -> {
            try { followRequestService.sendFollowRequest("A", "B"); }
            catch (Exception ignored) {} // idempotent under races
        });

        assertThat(relCount("A","FOLLOW_REQUEST","B")).isEqualTo(1L);
        assertThat(relCount("A","FOLLOWS","B")).isEqualTo(0L);
    }

    @Test
    @Timeout(60)
    @DisplayName("02) Concurrent accept -> one FOLLOWS, zero FOLLOW_REQUEST")
    void accept_concurrent_oneFollow_zeroPending() throws Exception {
        // seed one pending request A -> B
        neo.query("MATCH (a:User {id:'A'}),(b:User {id:'B'}) MERGE (a)-[:FOLLOW_REQUEST]->(b)").run();

        int N = 40;
        runFlood(N, () -> {
            try { followRequestService.acceptFollowRequest("B", "A"); } // toId, fromId
            catch (Exception ignored) {}
        });

        assertThat(relCount("A","FOLLOWS","B")).isEqualTo(1L);
        assertThat(relCount("A","FOLLOW_REQUEST","B")).isZero();
    }

    @Test
    @Timeout(60)
    @DisplayName("03) Concurrent deny -> zero FOLLOW_REQUEST (idempotent)")
    void deny_concurrent_zeroPending() throws Exception {
        // seed one pending request A -> B
        neo.query("MATCH (a:User {id:'A'}),(b:User {id:'B'}) MERGE (a)-[:FOLLOW_REQUEST]->(b)").run();

        int N = 40;
        runFlood(N, () -> {
            try { followRequestService.denyFollowRequest("B", "A"); } // toId, fromId
            catch (Exception ignored) {}
        });

        assertThat(relCount("A","FOLLOW_REQUEST","B")).isEqualTo(0L);
        assertThat(relCount("A","FOLLOWS","B")).isEqualTo(0L);
    }

    @Test
    @Timeout(60)
    @DisplayName("04) Concurrent block -> exactly ONE BLOCKS; all requests & follows cleaned both ways")
    void block_concurrent_singleBlock_allCleaned() throws Exception {
        // pre-wire both-directional follows + pending both ways to stress cleanup
        neo.query("""
            MATCH (a:User {id:'A'}),(b:User {id:'B'})
            MERGE (a)-[:FOLLOWS]->(b)
            MERGE (b)-[:FOLLOWS]->(a)
            MERGE (a)-[:FOLLOW_REQUEST]->(b)
            MERGE (b)-[:FOLLOW_REQUEST]->(a)
            """).run();

        int N = 30;
        runFlood(N, () -> {
            try { blockService.block("A", "B"); }  // A blocks B
            catch (Exception ignored) {}
        });

        // one BLOCKS from A->B
        assertThat(relCount("A","BLOCKS","B")).isEqualTo(1L);

        // no FOLLOWS either direction
        assertThat(relCountEitherDirection("A","FOLLOWS","B")).isEqualTo(0L);

        // no FOLLOW_REQUEST either direction
        assertThat(relCountEitherDirection("A","FOLLOW_REQUEST","B")).isEqualTo(0L);
    }

    @Test
    @Timeout(60)
    @DisplayName("05) Concurrent unblock -> ZERO BLOCKS (idempotent)")
    void unblock_concurrent_zeroBlocks() throws Exception {
        // seed block
        neo.query("MATCH (a:User {id:'A'}),(b:User {id:'B'}) MERGE (a)-[:BLOCKS]->(b)").run();

        int N = 40;
        runFlood(N, () -> {
            try { blockService.unblock("A", "B"); }
            catch (Exception ignored) {}
        });

        assertThat(relCount("A","BLOCKS","B")).isEqualTo(0L);
    }

    @Test
    @Timeout(60)
    @DisplayName("06) Send request under existing follow -> no extra edges (idempotent behavior)")
    void send_whenAlreadyFollowing_noop() throws Exception {
        // A already follows B
        neo.query("MATCH (a:User {id:'A'}),(b:User {id:'B'}) MERGE (a)-[:FOLLOWS]->(b)").run();

        int N = 25;
        runFlood(N, () -> {
            try { followRequestService.sendFollowRequest("A", "B"); }
            catch (Exception ignored) {}
        });

        assertThat(relCount("A","FOLLOWS","B")).isEqualTo(1L);
        assertThat(relCount("A","FOLLOW_REQUEST","B")).isEqualTo(0L);
    }
}
