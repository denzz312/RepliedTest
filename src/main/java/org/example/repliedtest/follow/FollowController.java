package org.example.repliedtest.follow;

import lombok.RequiredArgsConstructor;
import org.example.repliedtest.follow.dto.FollowRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/follows")
public class FollowController {

    private final FollowService service;

    @PostMapping("/requests")
    public ResponseEntity<Void> send(@RequestBody FollowRequest req) {
        service.sendFollowRequest(req.getFromUserId(), req.getToUserId());
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/requests/{toUserId}")
    public ResponseEntity<List<String>> listIncoming(@PathVariable String toUserId) {
        return ResponseEntity.ok(service.listIncomingRequests(toUserId));
    }
}
