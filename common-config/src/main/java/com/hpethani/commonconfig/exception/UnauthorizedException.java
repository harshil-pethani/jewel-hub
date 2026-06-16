package com.hpethani.commonconfig.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

public class UnauthorizedException extends RuntimeException{
    private String message;
    @Getter
    private HttpStatus status;

    public UnauthorizedException() {
    }

    public UnauthorizedException(String message) {
        super(message);
        this.message = message;
        this.status = HttpStatus.UNAUTHORIZED;
    }

    @Override
    public String getMessage() {
        return message;
    }

}
