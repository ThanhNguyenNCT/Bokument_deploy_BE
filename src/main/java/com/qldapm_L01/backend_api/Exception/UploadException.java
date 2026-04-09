package com.qldapm_L01.backend_api.Exception;

import org.springframework.http.HttpStatus;

public abstract class UploadException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;
    private final String userMessage;

    protected UploadException(String errorCode, HttpStatus httpStatus, String userMessage) {
        super(userMessage);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.userMessage = userMessage;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
