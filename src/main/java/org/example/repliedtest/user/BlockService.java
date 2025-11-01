package org.example.repliedtest.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.repliedtest.support.ApiExceptions;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockService {

    private final Neo4jClient neo;

    @Transactional
    public void block(String userId, String targetId) {
        if (userId == null || targetId == null || userId.equals(targetId)) {
            throw new ApiExceptions.BadRequest("cannot block yourself");
        }

        // Assuming that once the user is blocked, any existing follow/follow_request is removed.
        long ok = neo.query("""
                        MATCH (u:User {id:$u}), (t:User {id:$t})
                        MERGE (u)-[:BLOCKS]->(t)
                        WITH u,t
                        
                        // delete FOLLOWS u<->t
                        CALL {
                          WITH u,t
                          MATCH (u)-[r:FOLLOWS]->(t)
                          DELETE r
                          RETURN count(r) AS c1
                        }
                        CALL {
                          WITH u,t
                          MATCH (t)-[r:FOLLOWS]->(u)
                          DELETE r
                          RETURN count(r) AS c2
                        }
                        // delete FOLLOW_REQUEST u<->t
                        CALL {
                          WITH u,t
                          MATCH (u)-[r:FOLLOW_REQUEST]->(t)
                          DELETE r
                          RETURN count(r) AS c3
                        }
                        CALL {
                          WITH u,t
                          MATCH (t)-[r:FOLLOW_REQUEST]->(u)
                          DELETE r
                          RETURN count(r) AS c4
                        }
                        
                        RETURN 1 AS ok
                        """)
                .bind(userId).to("u")
                .bind(targetId).to("t")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);

        if (ok == 0L) {
            throw new ApiExceptions.NotFound("user not found");
        }

    }

    @Transactional
    public void unblock(String userId, String targetId) {
        if (userId == null || targetId == null || userId.equals(targetId)) {
            throw new ApiExceptions.BadRequest("cannot unblock yourself");
        }

        var res = neo.query("""
                        MATCH (u:User {id:$u}), (t:User {id:$t})
                        OPTIONAL MATCH (u)-[b:BLOCKS]->(t)
                        DELETE b
                        RETURN 1 AS ok
                        """)
                .bind(userId).to("u")
                .bind(targetId).to("t")
                .fetchAs(Integer.class)
                .one();

        if (res.isEmpty()) {
            throw new ApiExceptions.NotFound("user not found");
        }
    }
}
