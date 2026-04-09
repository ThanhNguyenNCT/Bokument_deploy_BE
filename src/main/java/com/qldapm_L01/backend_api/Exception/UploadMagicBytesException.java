package com.qldapm_L01.backend_api.Exception;

import org.springframework.http.HttpStatus;

public class UploadMagicBytesException extends UploadException {
    public UploadMagicBytesException(String userMessage) {
        super("UPLOAD_MAGIC_BYTES_INVALID", HttpStatus.BAD_REQUEST, userMessage);
    }
}
