package org.example.repliedtest.user;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("User")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNode {
    @Id
    private String id;            // UUID
    private String username;      // optional, unique
    private String birthdate;     // ISO YYYY-MM-DD
}
