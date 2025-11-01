package org.example.repliedtest.user;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = RepliedTestApplication.class)
@AutoConfigureMockMvc
class UserBlockIT {

    @Container
    static final Neo4jContainer<?> neo4j =
            new Neo4jContainer<>("neo4j:5.23").withAdminPassword("password");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.neo4j.uri", neo4j::getBoltUrl);
        r.add("spring.neo4j.authentication.username", () -> "neo4j");
        r.add("spring.neo4j.authentication.password", () -> "password");
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    Neo4jClient neo;

    @BeforeEach
    void seed() {
        neo.query("MATCH (n) DETACH DELETE n").run();
        neo.query("CREATE (:User {id:'U', username:'uly', birthdate:'1990-01-01'})").run();
        neo.query("CREATE (:User {id:'T', username:'tina', birthdate:'1991-02-02'})").run();

        // Pre-existing relationships both directions
        neo.query("""
                MATCH (u:User{id:'U'}),(t:User{id:'T'})
                MERGE (u)-[:FOLLOWS]->(t)
                MERGE (t)-[:FOLLOWS]->(u)
                MERGE (u)-[:FOLLOW_REQUEST]->(t)
                MERGE (t)-[:FOLLOW_REQUEST]->(u)
                """).run();
    }

    @Test
    void block_cleans_all_and_enforces_then_unblock_restores_request_ability() throws Exception {
        // 1) U blocks T
        mvc.perform(post("/users/U/block/T"))
                .andExpect(status().isNoContent());

        // 2) Verify cleanup
        var flagsAfterBlock = neo.query("""
                MATCH (u:User{id:'U'}),(t:User{id:'T'})
                RETURN
                  EXISTS( (u)-[:BLOCKS]->(t) ) AS blk,
                  EXISTS( (u)-[:FOLLOWS]->(t) ) AS u2t,
                  EXISTS( (t)-[:FOLLOWS]->(u) ) AS t2u,
                  EXISTS( (u)-[:FOLLOW_REQUEST]->(t) ) AS r_u2t,
                  EXISTS( (t)-[:FOLLOW_REQUEST]->(u) ) AS r_t2u
                """).fetch().one().orElseThrow();

        assertThat((Boolean) flagsAfterBlock.get("blk")).isTrue();
        assertThat((Boolean) flagsAfterBlock.get("u2t")).isFalse();
        assertThat((Boolean) flagsAfterBlock.get("t2u")).isFalse();
        assertThat((Boolean) flagsAfterBlock.get("r_u2t")).isFalse();
        assertThat((Boolean) flagsAfterBlock.get("r_t2u")).isFalse();

        // 3) follow_requests now disallowed both directions (either-side block)
        mvc.perform(post("/follows/requests")
                        .contentType("application/json")
                        .content("{\"fromUserId\":\"U\",\"toUserId\":\"T\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/follows/requests")
                        .contentType("application/json")
                        .content("{\"fromUserId\":\"T\",\"toUserId\":\"U\"}"))
                .andExpect(status().isForbidden());

        // 4) unblock
        mvc.perform(delete("/users/U/block/T"))
                .andExpect(status().isNoContent());

        // 5) requests are allowed again
        mvc.perform(post("/follows/requests")
                        .contentType("application/json")
                        .content("{\"fromUserId\":\"T\",\"toUserId\":\"U\"}"))
                .andExpect(status().isAccepted());

        // 6) unblock again
        mvc.perform(delete("/users/U/block/T"))
                .andExpect(status().isNoContent());
    }
}
