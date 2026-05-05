package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

/**
 * 登录请求参数
 */
@Data
public class LoginReqDTO {

    /** 手机号 */
    private String phone;
    /** 密码 */
    private String password;
}
