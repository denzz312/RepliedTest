package org.example.repliedtest.user;

import org.springframework.data.neo4j.repository.Neo4jRepository;

import java.util.Optional;

public interface UserRepository extends Neo4jRepository<UserNode, String> {
    boolean existsByUsername(String username);

    Optional<UserNode> findByUsername(String username);
}
