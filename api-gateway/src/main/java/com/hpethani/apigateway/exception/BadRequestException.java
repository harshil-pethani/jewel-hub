package com.hpethani.apigateway.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Custom exception for gateway-level bad request errors.
 *
 * Extends RuntimeException — message is stored in the parent class (no shadow field needed).
 * Caught by GlobalExceptionHandler which maps it to the appropriate HTTP status.
 */
public class BadRequestException extends RuntimeException {

    // Lombok @Getter generates getStatus() — no need for manual getter
    @Getter
    private final HttpStatus status;

    public BadRequestException(String message) {
        super(message);
        this.status = HttpStatus.BAD_REQUEST;
    }

    public BadRequestException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

}
