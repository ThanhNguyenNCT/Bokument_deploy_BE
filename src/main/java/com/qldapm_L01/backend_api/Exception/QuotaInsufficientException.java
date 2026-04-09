package com.qldapm_L01.backend_api.Exception;

public class QuotaInsufficientException extends RuntimeException {
    public QuotaInsufficientException(String message) {
        super(message);
    }
}
