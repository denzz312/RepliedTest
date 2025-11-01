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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = RepliedTestApplication.class)
@AutoConfigureMockMvc
class FollowAcceptDenyIT {

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
        neo.query("CREATE (:User {id:'F', username:'from', birthdate:'1990-01-01'})").run();
        neo.query("CREATE (:User {id:'T', username:'to',   birthdate:'1991-01-01'})").run();
        neo.query("MATCH (f:User{id:'F'}),(t:User{id:'T'}) MERGE (f)-[:FOLLOW_REQUEST]->(t)").run();
    }

    @Test
    void accept_then_reaccept_returns_404() throws Exception {
        // first accept -> 204
        mvc.perform(post("/follows/requests/T/accept/F"))
                .andExpect(status().isNoContent());

        // check: request removed, follows created
        var counts = neo.query("""
        MATCH (f:User{id:'F'}),(t:User{id:'T'})
        RETURN
          EXISTS( (f)-[:FOLLOW_REQUEST]->(t) ) AS pending,
          EXISTS( (f)-[:FOLLOWS]->(t) )       AS follows
        """).fetch().one().orElseThrow();
        assertThat((Boolean) counts.get("pending")).isFalse();
        assertThat((Boolean) counts.get("follows")).isTrue();

        // second accept -> 404
        mvc.perform(post("/follows/requests/T/accept/F"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deny_then_redeny_returns_404() throws Exception {
        // first deny -> 204
        mvc.perform(post("/follows/requests/T/deny/F"))
                .andExpect(status().isNoContent());

        // check: request removed, no follows edge
        var counts = neo.query("""
        MATCH (f:User{id:'F'}),(t:User{id:'T'})
        RETURN
          EXISTS( (f)-[:FOLLOW_REQUEST]->(t) ) AS pending,
          EXISTS( (f)-[:FOLLOWS]->(t) )       AS follows
        """).fetch().one().orElseThrow();
        assertThat((Boolean) counts.get("pending")).isFalse();
        assertThat((Boolean) counts.get("follows")).isFalse();

        // second deny -> 404
        mvc.perform(post("/follows/requests/T/deny/F"))
                .andExpect(status().isNotFound());
    }
}
