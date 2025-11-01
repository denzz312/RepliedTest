package org.example.repliedtest.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Users")
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserListsController {

    private final UserListService service;

    @Operation(
            summary = "List following (users that {userId} follows)",
            description = "Hides entries where the listed user blocks the viewer. Returns birthdate only for close friends (mutual FOLLOWS with viewer). Sorted by username (case-insensitive; nulls last; ties by id)."
    )

    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserNode.class))))
    @ApiResponse(responseCode = "403", description = "Forbidden (userId blocks viewer)")
    @ApiResponse(responseCode = "404", description = "Not Found (user/viewer not found)")
    @GetMapping(value = "/{userId}/following", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<UserNode>> following(
            @Parameter(description = "List owner") @PathVariable String userId,
            @Parameter(description = "Viewer id; used for visibility & close-friend check", required = true)
            @RequestParam("viewer") String viewerId
    ) {
        return ResponseEntity.ok(service.listFollowing(userId, viewerId));
    }

    @Operation(
            summary = "List followers (users that follow {userId})",
            description = "Hides entries where the listed user blocks the viewer. Returns birthdate only for close friends (mutual FOLLOWS with viewer). Sorted by username (case-insensitive; nulls last; ties by id)."
    )
    @ApiResponse(responseCode = "200", description = "OK",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserNode.class))))
    @ApiResponse(responseCode = "403", description = "Forbidden (userId blocks viewer)")
    @ApiResponse(responseCode = "404", description = "Not Found (user/viewer not found)")
    @GetMapping(value = "/{userId}/followers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<UserNode>> followers(
            @Parameter(description = "List owner") @PathVariable String userId,
            @Parameter(description = "Viewer id; used for visibility & close-friend check", required = true)
            @RequestParam("viewer") String viewerId
    ) {
        return ResponseEntity.ok(service.listFollowers(userId, viewerId));
    }
}
