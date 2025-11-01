package org.example.repliedtest.follow;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.example.repliedtest.follow.dto.FollowRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Follows")
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

    @PostMapping("/requests/{to}/accept/{from}")
    public ResponseEntity<Void> accept(@PathVariable String to, @PathVariable String from) {
        service.acceptFollowRequest(to, from);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/requests/{to}/deny/{from}")
    public ResponseEntity<Void> deny(@PathVariable String to, @PathVariable String from) {
        service.denyFollowRequest(to, from);
        return ResponseEntity.noContent().build();
    }

}
