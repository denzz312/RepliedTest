package org.example.repliedtest.support;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;
import org.springframework.boot.CommandLineRunner;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.demodata", havingValue = "true")
@DependsOn("neo4jSchemaInit") // constraints should be created first
public class DemoGraphSeeder implements CommandLineRunner {

    private final Neo4jClient neo;

    @Override
    @Transactional
    public void run(String... args) {
        // Skip if already seeded
        long already = neo.query("MATCH (s:Seed {key:$k}) RETURN count(s) AS c").bind("demo-10").to("k").fetchAs(Long.class).one().orElse(0L);
        if (already > 0) {
            log.info("Demo graph 'demo-10' already present; skipping.");
            return;
        }

        neo.query("""
                MERGE (alice:User {id:'U1'})  SET alice.username='alice',  alice.birthdate='1990-01-01'
                MERGE (bob:User   {id:'U2'})  SET bob.username='bob',      bob.birthdate='1990-02-02'
                MERGE (charlie:User {id:'U3'}) SET charlie.username='charlie', charlie.birthdate='1990-03-03'
                MERGE (diana:User {id:'U4'})  SET diana.username='diana',  diana.birthdate='1990-04-04'
                MERGE (eva:User   {id:'U5'})  SET eva.username='eva',      eva.birthdate='1990-05-05'
                MERGE (frank:User {id:'U6'})  SET frank.username='frank',  frank.birthdate='1990-06-06'
                MERGE (gary:User  {id:'U7'})  SET gary.username='gary',    gary.birthdate='1990-07-07'
                MERGE (helen:User {id:'U8'})  SET helen.username='helen',  helen.birthdate='1990-08-08'
                MERGE (ivan:User  {id:'U9'})  SET ivan.username='ivan',    ivan.birthdate='1990-09-09'
                MERGE (zoe:User   {id:'U10'}) SET zoe.username='zoe',      zoe.birthdate='1990-10-10'
                
                // FOLLOWS
                MERGE (alice)-[:FOLLOWS]->(bob)
                MERGE (bob)-[:FOLLOWS]->(alice)
                MERGE (charlie)-[:FOLLOWS]->(diana)
                MERGE (gary)-[:FOLLOWS]->(helen)
                MERGE (helen)-[:FOLLOWS]->(gary)
                MERGE (bob)-[:FOLLOWS]->(charlie)
                MERGE (diana)-[:FOLLOWS]->(gary)
                MERGE (eva)-[:FOLLOWS]->(alice)
                MERGE (ivan)-[:FOLLOWS]->(zoe)
                
                // Pending FOLLOW_REQUEST
                MERGE (frank)-[:FOLLOW_REQUEST]->(bob)
                MERGE (zoe)-[:FOLLOW_REQUEST]->(alice)
                
                // BLOCKS
                MERGE (helen)-[:BLOCKS]->(alice)
                MERGE (alice)-[:BLOCKS]->(frank)
                MERGE (zoe)-[:BLOCKS]->(ivan)
                """).run();


        neo.query("MERGE (:Seed {key:$k, ts:datetime()})").bind("demo-data").to("k").run();

        log.info("Seeded demo-data graph: 10 users + follows/requests/blocks.");
    }
}
