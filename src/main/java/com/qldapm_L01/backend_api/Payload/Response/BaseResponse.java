package com.qldapm_L01.backend_api.Payload.Response;

import lombok.Data;

@Data
public class BaseResponse {
    private int statusCode;
    private String message;
    private Object data;
}

