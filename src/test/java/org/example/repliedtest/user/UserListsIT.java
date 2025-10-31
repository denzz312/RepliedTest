package org.example.repliedtest.user;

import org.example.repliedtest.RepliedTestApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(classes = RepliedTestApplication.class)
@AutoConfigureMockMvc
class UserListsIT {

    @Container
    static final Neo4jContainer<?> neo4j = new Neo4jContainer<>("neo4j:5.23").withAdminPassword("password");

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
        // viewer V, owner U and entries E1 to E4
        neo.query("""
                CREATE (:User {id:'V', username:'viewer', birthdate:'1990-01-01'}),
                       (:User {id:'U', username:'owner',  birthdate:'1991-02-02'}),
                       (:User {id:'A', username:'amy',    birthdate:'1992-03-03'}),
                       (:User {id:'B', username:'bobby',  birthdate:'1993-04-04'}),
                       (:User {id:'C', username:'zoe',    birthdate:'1994-05-05'}),
                       (:User {id:'D',                    birthdate:'1995-06-06'})
                """).run();

        // U follows A, B, C, D
        neo.query("""
                MATCH (u:User{id:'U'}),(a:User{id:'A'}),(b:User{id:'B'}),(c:User{id:'C'}),(d:User{id:'D'})
                MERGE (u)-[:FOLLOWS]->(a)
                MERGE (u)-[:FOLLOWS]->(b)
                MERGE (u)-[:FOLLOWS]->(c)
                MERGE (u)-[:FOLLOWS]->(d)
                """).run();

        // Close-friend only with B: V <-> B mutual
        neo.query("""
                MATCH (v:User{id:'V'}),(b:User{id:'B'})
                MERGE (v)-[:FOLLOWS]->(b)
                MERGE (b)-[:FOLLOWS]->(v)
                """).run();

        // C blocks the viewer (should be hidden from lists to V)
        neo.query("""
                MATCH (c:User{id:'C'}),(v:User{id:'V'})
                MERGE (c)-[:BLOCKS]->(v)
                """).run();

        // Followers list: A and D follow U (A visible, D visible)
        neo.query("""
                MATCH (u:User{id:'U'}),(a:User{id:'A'}),(d:User{id:'D'})
                MERGE (a)-[:FOLLOWS]->(u)
                MERGE (d)-[:FOLLOWS]->(u)
                """).run();
    }

    @Test
    void following_filters_blocked_shows_birthdate_only_for_close_friends_sorted_by_username() throws Exception {
        mvc.perform(get("/users/U/following").param("viewer", "V")).andExpect(status().isOk())
                // order: A(amy), B(bobby), D(null username)  — C is hidden (blocks viewer)
                .andExpect(jsonPath("$[0].id").value("A")).andExpect(jsonPath("$[0].username").value("amy")).andExpect(jsonPath("$[0].birthdate").doesNotExist()) // not a close friend with V

                .andExpect(jsonPath("$[1].id").value("B")).andExpect(jsonPath("$[1].username").value("bobby")).andExpect(jsonPath("$[1].birthdate").value("1993-04-04")) // V <-> B mutual

                .andExpect(jsonPath("$[2].id").value("D")).andExpect(jsonPath("$[2].username").doesNotExist()).andExpect(jsonPath("$[2].birthdate").doesNotExist());
    }

    @Test
    void followers_filters_blocked_and_sorts() throws Exception {
        mvc.perform(get("/users/U/followers").param("viewer", "V")).andExpect(status().isOk())
                // Followers are A(amy) and D(null); C is unrelated; B does not follow U
                .andExpect(jsonPath("$[0].id").value("A")).andExpect(jsonPath("$[0].username").value("amy")).andExpect(jsonPath("$[0].birthdate").doesNotExist())

                .andExpect(jsonPath("$[1].id").value("D")).andExpect(jsonPath("$[1].username").doesNotExist()).andExpect(jsonPath("$[1].birthdate").doesNotExist());
    }

    @Test
    void owner_blocks_viewer_yields_403() throws Exception {
        neo.query("MATCH (u:User{id:'U'}),(v:User{id:'V'}) MERGE (u)-[:BLOCKS]->(v)").run();
        mvc.perform(get("/users/U/following").param("viewer", "V")).andExpect(status().isForbidden());
        mvc.perform(get("/users/U/followers").param("viewer", "V")).andExpect(status().isForbidden());
    }
}

