package org.example.repliedtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.Neo4jContainer;

import java.util.List;
import java.util.Map;

@Testcontainers
@SpringBootTest(
        classes = RepliedTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class SchemaConstraintsIT {

    @Container
    static final Neo4jContainer<?> neo4j =
            new Neo4jContainer<>("neo4j:5.23")
                    .withAdminPassword("password");

    @DynamicPropertySource
    static void neo4jProps(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> "password");
    }

    @Autowired
    Neo4jClient neo;

    @Test
    void constraintsExist_afterStartup() {
        var row = neo.query("""
        SHOW CONSTRAINTS YIELD name
        WHERE name IN ['user_id','user_handle']
        RETURN collect(name) AS names
      """).fetch().one().orElseThrow();

        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) row.get("names");
        Assertions.assertTrue(names.containsAll(List.of("user_id","user_handle")),
                "Expected constraints user_id and user_handle to exist");
    }

    @Test
    void duplicateHandle_isRejected() {
        // First insert with handle 'same' succeeds
        neo.query("CREATE (:User {id:$id1, handle:$h})")
                .bindAll(Map.of("id1","u1","h","same"))
                .run();

        // Second insert with the same handle should violate unique constraint
        Executable secondInsert = () -> neo.query("CREATE (:User {id:$id2, handle:$h})")
                .bindAll(Map.of("id2","u2","h","same"))
                .run();

        Assertions.assertThrows(RuntimeException.class, secondInsert,
                "Expected a unique constraint violation on User.handle");
    }
}
