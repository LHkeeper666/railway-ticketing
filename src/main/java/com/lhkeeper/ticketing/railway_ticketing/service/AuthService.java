package com.lhkeeper.ticketing.railway_ticketing.service;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.LoginReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RegisterReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.LoginRespDTO;

/**
 * 认证服务接口，提供用户登录和注册
 */
public interface AuthService {

    /**
     * 手机号+密码登录，返回 JWT 令牌
     */
    LoginRespDTO login(LoginReqDTO reqDTO);

    /**
     * 用户注册
     */
    void register(RegisterReqDTO reqDTO);
}
