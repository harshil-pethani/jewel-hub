package com.hpethani.commonconfig.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

public class BadRequestException extends RuntimeException{
    private String message;
    @Getter
    private HttpStatus status;

    public BadRequestException() {
    }

    public BadRequestException(String message) {
        super(message);
        this.message = message;
        this.status = HttpStatus.BAD_REQUEST;
    }

    @Override
    public String getMessage() {
        return message;
    }

}
