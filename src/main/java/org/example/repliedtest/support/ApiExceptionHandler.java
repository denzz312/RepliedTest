package org.example.repliedtest.support;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

import static org.example.repliedtest.support.ApiExceptions.*;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(BadRequest.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequest ex) {
        return body(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage());
    }

    @ExceptionHandler(Forbidden.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(Forbidden ex) {
        return body(HttpStatus.FORBIDDEN, "forbidden", ex.getMessage());
    }

    @ExceptionHandler(NotFound.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFound ex) {
        return body(HttpStatus.NOT_FOUND, "not_found", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                Map.of("error", code, "message", message, "ts", Instant.now().toString())
        );
    }
}
