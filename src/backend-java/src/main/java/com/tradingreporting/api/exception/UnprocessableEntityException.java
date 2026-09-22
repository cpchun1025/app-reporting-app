package com.tradingreporting.api.exception;

import org.springframework.http.HttpStatus;

public class UnprocessableEntityException extends ApiException {
    public UnprocessableEntityException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
