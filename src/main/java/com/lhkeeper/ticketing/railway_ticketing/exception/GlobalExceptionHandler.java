package com.lhkeeper.ticketing.railway_ticketing.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

 import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * 统一异常处理，捕获所有 Exception 并返回失败响应
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handle(Exception e) {
         return Result.fail(e.getMessage());
     }
}
