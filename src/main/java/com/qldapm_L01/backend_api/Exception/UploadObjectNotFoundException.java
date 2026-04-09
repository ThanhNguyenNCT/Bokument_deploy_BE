package com.qldapm_L01.backend_api.Exception;

import org.springframework.http.HttpStatus;

public class UploadObjectNotFoundException extends UploadException {
    public UploadObjectNotFoundException(String userMessage) {
        super("UPLOAD_OBJECT_NOT_FOUND", HttpStatus.NOT_FOUND, userMessage);
    }
}
