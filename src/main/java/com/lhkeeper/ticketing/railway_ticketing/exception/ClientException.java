package com.lhkeeper.ticketing.railway_ticketing.exception;

/**
 * 客户端异常（HTTP 400），用于参数校验失败、限流拒绝等场景
 */
public class ClientException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public ClientException(String message) {
        super(message);
    }
}
