package org.example.repliedtest.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
