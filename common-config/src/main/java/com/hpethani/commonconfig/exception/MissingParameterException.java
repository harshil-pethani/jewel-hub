package com.hpethani.commonconfig.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

public class MissingParameterException extends RuntimeException{
    private String message;
    @Getter
    private HttpStatus status;

    public MissingParameterException() {
    }

    public MissingParameterException(String message) {
        super(message);
        this.message = message;
        this.status = HttpStatus.BAD_REQUEST;
    }

    @Override
    public String getMessage() {
        return message;
    }

}
