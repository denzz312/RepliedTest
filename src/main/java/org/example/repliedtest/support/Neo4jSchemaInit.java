package org.example.repliedtest.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Neo4jSchemaInit implements CommandLineRunner {
    private final Neo4jClient neo4j;

    public Neo4jSchemaInit(Neo4jClient neo4j) {
        this.neo4j = neo4j;
    }

    @Override
    public void run(String... args) {
        try {
            create("CREATE CONSTRAINT user_id IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE");
            create("CREATE CONSTRAINT user_handle IF NOT EXISTS FOR (u:User) REQUIRE u.handle IS UNIQUE");
            log.info("Neo4j schema constraints ensured (user_id, user_handle).");
        } catch (Exception e) {
            // Don’t kill the app if DB is not reachable
            log.error("Skipping Neo4j schema init (DB not reachable): {}", e.getMessage());
        }
    }

    private void create(String cypher) {
        neo4j.query(cypher).run();
    }
}
