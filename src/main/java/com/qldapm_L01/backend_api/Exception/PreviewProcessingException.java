package com.qldapm_L01.backend_api.Exception;

import org.springframework.http.HttpStatus;

public class PreviewProcessingException extends UploadException {
    public PreviewProcessingException(String userMessage) {
        super("UPLOAD_PROCESSING_FAILED_PREVIEW", HttpStatus.INTERNAL_SERVER_ERROR, userMessage);
    }
}
