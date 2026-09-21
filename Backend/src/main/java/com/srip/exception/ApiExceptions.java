package com.srip.exception;

/** The exceptions the application throws deliberately, each mapped to a status. */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** 404 - the requested entity does not exist. */
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }

        public static NotFoundException of(String entity, Object id) {
            return new NotFoundException(entity + " not found: " + id);
        }
    }

    /** 400 - the request is well-formed but cannot be satisfied. */
    public static class BadRequestException extends RuntimeException {
        public BadRequestException(String message) {
            super(message);
        }
    }

    /** 409 - the request conflicts with existing state. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    /** 401 - credentials or refresh token rejected. */
    public static class AuthenticationFailedException extends RuntimeException {
        public AuthenticationFailedException(String message) {
            super(message);
        }
    }

    /** 422 - the CSV was readable but not usable. */
    public static class CsvValidationException extends RuntimeException {
        public CsvValidationException(String message) {
            super(message);
        }
    }

    /** 502 - the Claude API was reachable but the call did not succeed. */
    public static class AiGenerationException extends RuntimeException {
        public AiGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
