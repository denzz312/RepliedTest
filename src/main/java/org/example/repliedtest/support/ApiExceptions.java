package org.example.repliedtest.support;

public final class ApiExceptions {
    private ApiExceptions() {}

    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) { super(message); }
    }

    public static class Forbidden extends RuntimeException {
        public Forbidden(String message) { super(message); }
    }

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }
}