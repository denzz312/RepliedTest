package org.example.repliedtest.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.repliedtest.support.ApiExceptions;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository users;
    private final Neo4jClient neo;

    @Transactional
    public UserNode createUser(String rawUsername) {
        String username = normalizeUsername(rawUsername);

        if (username != null && users.existsByUsername(username)) {
            throw new ApiExceptions.BadRequest("username already taken");
        }

        UserNode toSave = UserNode.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .birthdate(randomBirthdateIso())
                .build();

        try {
            return users.save(toSave);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiExceptions.BadRequest("username already taken");
        }
    }

    @Transactional(readOnly = true)
    public UserNode getVisibleUser(String id, String viewerId) {
        UserNode u = users.findById(id)
                .orElseThrow(() -> new ApiExceptions.NotFound("user not found: " + id));

        if (viewerId != null && isBlockedByTarget(id, viewerId)) {
            throw new ApiExceptions.Forbidden("viewer is blocked by user: " + id);
        }
        return u;
    }

    private boolean isBlockedByTarget(String targetId, String viewerId) {
        return neo.query("""
                          RETURN EXISTS( (:User {id:$target})-[:BLOCKS]->(:User {id:$viewer}) ) AS blocked
                        """)
                .bind(targetId).to("target")
                .bind(viewerId).to("viewer")
                .fetchAs(Boolean.class).one().orElse(false);
    }

    private static String normalizeUsername(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String randomBirthdateIso() {
        long start = LocalDate.of(1970, 1, 1).toEpochDay();
        long end = LocalDate.of(2005, 12, 31).toEpochDay();
        long rnd = ThreadLocalRandom.current().nextLong(start, end + 1);
        return LocalDate.ofEpochDay(rnd).toString();
    }
}
