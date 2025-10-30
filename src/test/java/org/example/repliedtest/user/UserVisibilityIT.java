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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(classes = RepliedTestApplication.class)
@AutoConfigureMockMvc
class UserVisibilityIT {

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
    void setUp() {
        neo.query("MATCH (n) DETACH DELETE n").run();
        neo.query("CREATE (:User {id:'A', username:'alice', birthdate:'1990-01-01'})").run();
        neo.query("CREATE (:User {id:'B', username:'bob',   birthdate:'1992-02-02'})").run();
    }


    @Test
    void viewer_blocked_by_target_gets_403() throws Exception {
        // A blocks B  => GET /users/A?viewer=B must be 403
        neo.query("MATCH (a:User{id:'A'}),(b:User{id:'B'}) MERGE (a)-[:BLOCKS]->(b)").run();

        mvc.perform(get("/users/A").param("viewer", "B"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
    }

    @Test
    void viewer_not_blocked_gets_200() throws Exception {
        mvc.perform(get("/users/A").param("viewer", "B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("A"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.birthdate").value("1990-01-01"));
    }
}
