package com.lhkeeper.ticketing.railway_ticketing.common.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.Optional;

@Data
@Accessors(chain = true)
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 5679018624309023727L;

    /**
     * 正确返回码
     */
    public static final String SUCCESS_CODE = "0";

    /**
     * 返回码
     */
    private String code;

    /**
     * 返回消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 请求ID
     */
    private String requestId;

    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }

    /**
     * 构造成功响应
     */
    public static Result<Void> success() {
        return new Result<Void>()
                .setCode(Result.SUCCESS_CODE);
    }

    /**
     * 构造带返回数据的成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<T>()
                .setCode(Result.SUCCESS_CODE)
                .setData(data);
    }

    /**
     * 构建服务端失败响应
     */
//    protected static Result<Void> failure() {
//        return new Result<Void>()
//                .setCode(BaseErrorCode.SERVICE_ERROR.code())
//                .setMessage(BaseErrorCode.SERVICE_ERROR.message());
//    }

//    /**
//     * 通过 {@link AbstractException} 构建失败响应
//     */
//    protected static Result<Void> failure(AbstractException abstractException) {
//        String errorCode = Optional.ofNullable(abstractException.getErrorCode())
//                .orElse(BaseErrorCode.SERVICE_ERROR.code());
//        String errorMessage = Optional.ofNullable(abstractException.getErrorMessage())
//                .orElse(BaseErrorCode.SERVICE_ERROR.message());
//        return new Result<Void>()
//                .setCode(errorCode)
//                .setMessage(errorMessage);
//    }

    /**
     * 通过 errorCode、errorMessage 构建失败响应
     */
    protected static Result<Void> failure(String errorCode, String errorMessage) {
        return new Result<Void>()
                .setCode(errorCode)
                .setMessage(errorMessage);
    }
}

