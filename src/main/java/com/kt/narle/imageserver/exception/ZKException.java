package com.kt.narle.imageserver.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ZKException extends RuntimeException{

    public ZKException(String message) { super(message);
    }
    public ZKException(String message, Throwable cause) {
        super(message, cause);
    }
}
