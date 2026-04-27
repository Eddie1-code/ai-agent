package com.xcw.aiagentbackend.common;


import com.xcw.aiagentbackend.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

  

/**
 * 通用响应类
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    private String requestId;

    private LocalDateTime timestamp;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
        this.requestId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
