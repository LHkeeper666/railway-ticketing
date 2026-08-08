package com.lhkeeper.ticketing.railway_ticketing.interceptor;

import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器（order=2，在 JwtInterceptor 之后），校验当前用户 role=1
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var userInfo = UserContext.get();
        if (userInfo == null || userInfo.getRole() == null || userInfo.getRole() != 1) {
            throw new ClientException("无管理员权限");
        }
        return true;
    }
}
