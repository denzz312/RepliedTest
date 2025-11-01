package org.example.repliedtest.user;

import lombok.RequiredArgsConstructor;
import org.example.repliedtest.support.ApiExceptions;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserListService {
    private final Neo4jClient neo;

    private void assertCanViewUser(String userId, String viewerId) {
        var row = neo.query("""
                MATCH (u:User {id:$u}), (v:User {id:$v})
                RETURN EXISTS( (u)-[:BLOCKS]->(v) ) AS blocked
                """).bind(userId).to("u").bind(viewerId).to("v").fetch().one();

        if (row.isEmpty()) throw new ApiExceptions.NotFound("user/viewer not found");
        if (Boolean.TRUE.equals(row.get().get("blocked")))
            throw new ApiExceptions.Forbidden("viewer is blocked by user");
    }

    @Transactional(readOnly = true)
    public List<UserNode> listFollowing(String userId, String viewerId) {
        assertCanViewUser(userId, viewerId);
        return (List<UserNode>) neo.query("""
                MATCH (u:User {id:$u}), (v:User {id:$v})
                MATCH (u)-[:FOLLOWS]->(e:User)
                WHERE NOT (e)-[:BLOCKS]->(v)
                WITH e, v,
                     (EXISTS( (v)-[:FOLLOWS]->(e) ) AND EXISTS( (e)-[:FOLLOWS]->(v) )) AS close
                RETURN e.id AS id,
                       e.username AS username,
                       CASE WHEN close THEN e.birthdate ELSE NULL END AS birthdate
                ORDER BY CASE WHEN e.username IS NULL THEN 1 ELSE 0 END,
                         toLower(e.username), e.id
                """).bind(userId).to("u").bind(viewerId).to("v").fetchAs(UserNode.class).mappedBy((t, r) -> UserNode.builder().id(r.get("id").asString()).username(r.get("username").isNull() ? null : r.get("username").asString()).birthdate(r.get("birthdate").isNull() ? null : r.get("birthdate").asString()).build()).all();
    }

    @Transactional(readOnly = true)
    public List<UserNode> listFollowers(String userId, String viewerId) {
        assertCanViewUser(userId, viewerId);
        return (List<UserNode>) neo.query("""
                MATCH (u:User {id:$u}), (v:User {id:$v})
                MATCH (e:User)-[:FOLLOWS]->(u)
                WHERE NOT (e)-[:BLOCKS]->(v)
                WITH e, v,
                     (EXISTS( (v)-[:FOLLOWS]->(e) ) AND EXISTS( (e)-[:FOLLOWS]->(v) )) AS close
                RETURN e.id AS id,
                       e.username AS username,
                       CASE WHEN close THEN e.birthdate ELSE NULL END AS birthdate
                ORDER BY CASE WHEN e.username IS NULL THEN 1 ELSE 0 END,
                         toLower(e.username), e.id
                """).bind(userId).to("u").bind(viewerId).to("v").fetchAs(UserNode.class).mappedBy((t, r) -> UserNode.builder().id(r.get("id").asString()).username(r.get("username").isNull() ? null : r.get("username").asString()).birthdate(r.get("birthdate").isNull() ? null : r.get("birthdate").asString()).build()).all();
    }
}

