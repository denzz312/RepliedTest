package org.example.repliedtest.follow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.repliedtest.support.ApiExceptions;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    private final Neo4jClient neo;

    @Transactional
    public void sendFollowRequest(String fromId, String toId) {
        // 1) no self
        if (fromId == null || toId == null || fromId.equals(toId)) {
            throw new ApiExceptions.BadRequest("cannot request to follow yourself");
        }

        // 2) ensure users exist; compute flags in one round-trip
        var flags = neo.query("""
        MATCH (f:User {id:$fromId})
        MATCH (t:User {id:$toId})
        RETURN
          true AS bothExist,
          EXISTS( (f)-[:BLOCKS]->(t) ) OR EXISTS( (t)-[:BLOCKS]->(f) ) AS blocked,
          EXISTS( (f)-[:FOLLOWS]->(t) ) AS alreadyFollows,
          EXISTS( (f)-[:FOLLOW_REQUEST]->(t) ) AS alreadyRequested
        """)
                .bind(fromId).to("fromId")
                .bind(toId).to("toId")
                .fetch()
                .one()
                .orElse(null);

        if (flags == null || flags.get("bothExist") == null) {
            // one or both users missing
            throw new ApiExceptions.NotFound("user not found");
        }

        boolean blocked = (boolean) flags.get("blocked");
        boolean alreadyFollows = (boolean) flags.get("alreadyFollows");
        boolean alreadyRequested = (boolean) flags.get("alreadyRequested");

        if (blocked) {
            // either direction is blocked
            throw new ApiExceptions.Forbidden("request disallowed due to block");
        }

        // 3) idempotent: if already following OR already requested => 202 (no-op)
        if (alreadyFollows || alreadyRequested) {
            return;
        }

        // 4) create request (idempotent MERGE)
        neo.query("""
        MATCH (f:User {id:$fromId})
        MATCH (t:User {id:$toId})
        MERGE (f)-[r:FOLLOW_REQUEST]->(t)
        ON CREATE SET r.ts = timestamp()
        """)
                .bind(fromId).to("fromId")
                .bind(toId).to("toId")
                .run();
    }

    @Transactional(readOnly = true)
    public List<String> listIncomingRequests(String toUserId) {
        // ensure target exists
        var exists = neo.query("MATCH (t:User {id:$id}) RETURN count(t) AS c")
                .bind(toUserId).to("id")
                .fetchAs(Long.class)
                .one()
                .orElse(0L);
        if (exists == 0L) {
            throw new ApiExceptions.NotFound("user not found: " + toUserId);
        }

        // only pending requests; exclude any where follow already exists; order by username (nulls last)
        return (List<String>) neo.query("""
        MATCH (from:User)-[:FOLLOW_REQUEST]->(to:User {id:$to})
        WHERE NOT (from)-[:FOLLOWS]->(to)
        WITH from
        ORDER BY CASE WHEN from.username IS NULL THEN 1 ELSE 0 END, toLower(from.username), from.id
        RETURN from.id AS id
        """)
                .bind(toUserId).to("to")
                .fetchAs(String.class)
                .mappedBy((t, r) -> r.get("id").asString())
                .all();
    }

    @Transactional
    public void acceptFollowRequest(String toId, String fromId) {
        // noly proceed if a pending request exists
        var updated = neo.query("""
      MATCH (t:User {id:$to})<-[r:FOLLOW_REQUEST]-(f:User {id:$from})
      MERGE (f)-[:FOLLOWS]->(t)
      DELETE r
      RETURN 1 AS updated
      """)
                .bind(toId).to("to")
                .bind(fromId).to("from")
                .fetchAs(Integer.class)
                .one();

        if (updated.isEmpty()) {
            throw new ApiExceptions.NotFound("no pending follow request");
        }
    }

    @Transactional
    public void denyFollowRequest(String toId, String fromId) {
        var updated = neo.query("""
      MATCH (t:User {id:$to})<-[r:FOLLOW_REQUEST]-(f:User {id:$from})
      DELETE r
      RETURN 1 AS updated
      """)
                .bind(toId).to("to")
                .bind(fromId).to("from")
                .fetchAs(Integer.class)
                .one();

        if (updated.isEmpty()) {
            throw new ApiExceptions.NotFound("no pending follow request");
        }
    }
}

