package com.tradingreporting.api.exception;

import org.springframework.http.HttpStatus;

/** Base type for API errors rendered as {@code {"detail": "..."}}, mirroring FastAPI's HTTPException. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
