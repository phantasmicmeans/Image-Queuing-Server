package com.kt.narle.imageserver.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DataInvalidException extends RuntimeException{
    public DataInvalidException() {
        super();
    }
    public DataInvalidException(String message) {
        super(message);
    }
}
