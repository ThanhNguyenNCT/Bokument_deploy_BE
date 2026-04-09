package com.qldapm_L01.backend_api.Exception;

import org.springframework.http.HttpStatus;

public class UploadInvalidStateException extends UploadException {
    public UploadInvalidStateException(String userMessage) {
        super("UPLOAD_STATUS_INVALID", HttpStatus.CONFLICT, userMessage);
    }
}
