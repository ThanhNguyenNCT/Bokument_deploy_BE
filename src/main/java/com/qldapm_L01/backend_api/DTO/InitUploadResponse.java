package com.qldapm_L01.backend_api.DTO;

import lombok.Data;

import java.util.UUID;

@Data
public class InitUploadResponse {
    private UUID documentId;
    private String uploadUrl;
    private String objectKey;
}
