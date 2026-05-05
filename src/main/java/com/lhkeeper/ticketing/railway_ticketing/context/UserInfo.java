package com.lhkeeper.ticketing.railway_ticketing.context;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 用户信息 DTO，用于通过 ThreadLocal 在请求上下文中传递
 */
@Data
@AllArgsConstructor
public class UserInfo {

    private Long userId;
    private String username;
    private String phone;
}
