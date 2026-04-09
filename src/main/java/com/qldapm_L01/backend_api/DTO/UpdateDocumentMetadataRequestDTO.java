package com.qldapm_L01.backend_api.DTO;

import lombok.Data;

import java.util.List;

@Data
public class UpdateDocumentMetadataRequestDTO {
    private String title;
    private String description;
    private List<String> tags;
}
