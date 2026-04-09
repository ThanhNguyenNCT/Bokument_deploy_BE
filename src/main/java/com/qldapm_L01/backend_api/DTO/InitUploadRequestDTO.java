package com.qldapm_L01.backend_api.DTO;

import lombok.Data;

import java.util.List;

@Data
public class InitUploadRequestDTO {
    private String title;
    private List<String> tags;
    private String description;
    private String fileName;
    private String ext;
}
