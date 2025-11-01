package org.example.repliedtest.follow.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowRequest {
    private String fromUserId;
    private String toUserId;
}