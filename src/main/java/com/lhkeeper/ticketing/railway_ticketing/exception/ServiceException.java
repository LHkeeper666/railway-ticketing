package com.lhkeeper.ticketing.railway_ticketing.exception;

/**
 * 服务端异常（HTTP 500），用于业务逻辑失败、系统繁忙等场景
 */
public class ServiceException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public ServiceException(String message) {
        super(message);
    }
}
