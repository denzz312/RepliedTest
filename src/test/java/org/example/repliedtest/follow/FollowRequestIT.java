package org.example.repliedtest.follow;

import org.example.repliedtest.RepliedTestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(classes = RepliedTestApplication.class)
@AutoConfigureMockMvc
class FollowRequestIT {

    @Container
    static final Neo4jContainer<?> neo4j =
            new Neo4jContainer<>("neo4j:5.23").withAdminPassword("password");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.neo4j.uri", neo4j::getBoltUrl);
        r.add("spring.neo4j.authentication.username", () -> "neo4j");
        r.add("spring.neo4j.authentication.password", () -> "password");
    }

    @Autowired MockMvc mvc;
    @Autowired Neo4jClient neo;

    @BeforeEach
    void seed() {
        neo.query("MATCH (n) DETACH DELETE n").run();
        neo.query("CREATE (:User {id:'A', username:'amy', birthdate:'1990-01-01'})").run();
        neo.query("CREATE (:User {id:'B', username:'Bob', birthdate:'1991-01-01'})").run();
        neo.query("CREATE (:User {id:'C', username:'zoe', birthdate:'1992-01-01'})").run();
        neo.query("CREATE (:User {id:'D', birthdate:'1993-01-01'})").run(); // null username
        neo.query("CREATE (:User {id:'T', username:'target', birthdate:'1994-01-01'})").run();
    }

    @Test
    void send_idempotent_repeat_returns_202() throws Exception {
        mvc.perform(post("/follows/requests")
                        .contentType("application/json")
                        .content("{\"fromUserId\":\"A\",\"toUserId\":\"T\"}"))
                .andExpect(status().isAccepted());

        // repeat
        mvc.perform(post("/follows/requests")
                        .contentType("application/json")
                        .content("{\"fromUserId\":\"A\",\"toUserId\":\"T\"}"))
                .andExpect(status().isAccepted());

        // only one relationship exists
        var cnt = neo.query("""
        MATCH (:User{id:'A'})-[r:FOLLOW_REQUEST]->(:User{id:'T'}) RETURN count(r) AS c
      """).fetchAs(Long.class).one().orElse(0L);
        assert cnt == 1L;
    }

    @Test
    void send_blocked_either_direction_403() throws Exception {
        // T blocks A
        neo.query("MATCH (a:User{id:'A'}),(t:User{id:'T'}) MERGE (t)-[:BLOCKS]->(a)").run();

        mvc.perform(post("/follows/requests")
                        .contentType("application/json")
                        .content("{\"fromUserId\":\"A\",\"toUserId\":\"T\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_incoming_sorted_by_username_nulls_last() throws Exception {
        neo.query("""
        MATCH (a:User{id:'A'}),(b:User{id:'B'}),(c:User{id:'C'}),(d:User{id:'D'}),(t:User{id:'T'})
        MERGE (a)-[:FOLLOW_REQUEST]->(t)
        MERGE (b)-[:FOLLOW_REQUEST]->(t)
        MERGE (c)-[:FOLLOW_REQUEST]->(t)
        MERGE (d)-[:FOLLOW_REQUEST]->(t)
        """).run();

        mvc.perform(get("/follows/requests/T"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("A"))
                .andExpect(jsonPath("$[1]").value("B"))
                .andExpect(jsonPath("$[2]").value("C"))
                .andExpect(jsonPath("$[3]").value("D"));
    }

    @Test
    void send_when_already_following_is_idempotent_202() throws Exception {
        // Pretend follow already exists
        neo.query("MATCH (a:User{id:'A'}),(t:User{id:'T'}) MERGE (a)-[:FOLLOWS]->(t)").run();

        mvc.perform(post("/follows/requests")
                        .contentType("application/json")
                        .content("{\"fromUserId\":\"A\",\"toUserId\":\"T\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void send_self_400_and_unknown_404() throws Exception {
        mvc.perform(post("/follows/requests")
                        .contentType("application/json")
                        .content("{\"fromUserId\":\"A\",\"toUserId\":\"A\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/follows/requests")
                        .contentType("application/json")
                        .content("{\"fromUserId\":\"X\",\"toUserId\":\"T\"}"))
                .andExpect(status().isNotFound());
    }
}

