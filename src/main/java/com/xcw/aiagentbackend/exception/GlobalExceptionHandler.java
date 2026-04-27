package com.xcw.aiagentbackend.exception;

import com.xcw.aiagentbackend.common.BaseResponse;
import com.xcw.aiagentbackend.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
@Hidden
public class GlobalExceptionHandler {

//    @ExceptionHandler(NotLoginException.class)
//    public BaseResponse<?> notLoginException(NotLoginException e) {
//        log.error("NotLoginException", e);
//        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, e.getMessage());
//    }
//
//    @ExceptionHandler(NotPermissionException.class)
//    public BaseResponse<?> notPermissionExceptionHandler(NotPermissionException e) {
//        log.error("NotPermissionException", e);
//        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
//    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public BaseResponse<?> illegalArgumentExceptionHandler(IllegalArgumentException e) {
        log.warn("IllegalArgumentException", e);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, e.getMessage());
    }
}
