package com.qldapm_L01.backend_api.Exception;

import org.springframework.http.HttpStatus;

public class UploadValidationException extends UploadException {
    public UploadValidationException(String userMessage) {
        super("UPLOAD_INIT_INVALID_INPUT", HttpStatus.BAD_REQUEST, userMessage);
    }
}
