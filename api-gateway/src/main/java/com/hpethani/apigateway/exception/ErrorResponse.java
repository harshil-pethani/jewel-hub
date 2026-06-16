package com.hpethani.apigateway.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * Standard error response body returned to clients on failures.
 *
 * Uses int statusCode instead of HttpStatus enum to ensure
 * Jackson serializes it as a number (e.g., 401) not an object.
 */
public class ErrorResponse {

    private String message;

    // int instead of HttpStatus — ensures JSON shows 401, not {"value":401,"reasonPhrase":"Unauthorized"}
    private int status;

    private String error;

    private LocalDateTime timestamp;

    public ErrorResponse() {}

    public ErrorResponse(String message, HttpStatus httpStatus) {
        this.message = message;
        this.status = httpStatus.value();
        this.error = httpStatus.getReasonPhrase();
        this.timestamp = LocalDateTime.now();
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
