package com.qldapm_L01.backend_api.Exception;

import org.springframework.http.HttpStatus;

public class UploadSizeExceededException extends UploadException {
    public UploadSizeExceededException(String userMessage) {
        super("UPLOAD_SIZE_EXCEEDED", HttpStatus.BAD_REQUEST, userMessage);
    }
}
