package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

/**
 * 注册请求参数
 */
@Data
public class RegisterReqDTO {

    /** 手机号 */
    private String phone;
    /** 密码 */
    private String password;
    /** 用户名 */
    private String username;
}
