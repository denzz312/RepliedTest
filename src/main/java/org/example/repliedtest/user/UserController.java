package org.example.repliedtest.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.example.repliedtest.user.dto.CreateUserRequest;
import org.example.repliedtest.user.dto.UserResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor

@RequestMapping("/users")
public class UserController {

    private final UserService service;
    private final BlockService blockService;

    @Operation(summary = "Create user",
            description = "Optional unique username; returns created user with random birthdate.")
    @ApiResponse(responseCode = "201", description = "Created",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "400", description = "Bad request",
            content = @Content(mediaType = "application/json"))
    @PostMapping
    public ResponseEntity<UserResponse> create(@RequestBody(required = false) CreateUserRequest req) {
        String username = (req == null) ? null : req.getUsername();
        UserNode saved = service.createUser(username);
        UserResponse body = UserResponse.builder()
                .id(saved.getId())
                .username(saved.getUsername())
                .birthdate(saved.getBirthdate())
                .build();
        return ResponseEntity.created(URI.create("/users/" + body.getId())).body(body);
    }

    @Operation(summary = "Get user (visibility gate)",
            description = "Returns user by id. If {id} blocks ?viewer={viewerId}, returns 403.")
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden (viewer blocked)")
    @ApiResponse(responseCode = "404", description = "Not Found")
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> get(
            @PathVariable String id,
            @RequestParam(name = "viewer", required = false) String viewerId
    ) {
        UserNode u = service.getVisibleUser(id, viewerId);
        return ResponseEntity.ok(UserResponse.builder()
                .id(u.getId())
                .username(u.getUsername())
                .birthdate(u.getBirthdate())
                .build());
    }

    @Operation(summary = "Block user",
            description = "Creates BLOCKS(userId -> targetId) and removes any FOLLOWS/FOLLOW_REQUEST in both directions.")
    @ApiResponse(responseCode = "204", description = "No Content (blocked & cleaned)")
    @ApiResponse(responseCode = "400", description = "Bad Request (self-block)")
    @ApiResponse(responseCode = "404", description = "Not Found (unknown userId/targetId)")
    @PostMapping("/{userId}/block/{targetId}")
    public ResponseEntity<Void> block(
            @Parameter(description = "Blocking user id") @PathVariable String userId,
            @Parameter(description = "Target to be blocked") @PathVariable String targetId) {
        blockService.block(userId, targetId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Unblock user",
            description = "Removes BLOCKS(userId→targetId). Idempotent if already unblocked.")
    @ApiResponse(responseCode = "204", description = "No Content (unblocked or already not blocked)")
    @ApiResponse(responseCode = "400", description = "Bad Request (self-unblock)")
    @ApiResponse(responseCode = "404", description = "Not Found (unknown userId/targetId)")
    @DeleteMapping("/{userId}/block/{targetId}")
    public ResponseEntity<Void> unblock(
            @Parameter(description = "Blocking user id") @PathVariable String userId,
            @Parameter(description = "Target to be unblocked") @PathVariable String targetId) {
        blockService.unblock(userId, targetId);
        return ResponseEntity.noContent().build();
    }
}
