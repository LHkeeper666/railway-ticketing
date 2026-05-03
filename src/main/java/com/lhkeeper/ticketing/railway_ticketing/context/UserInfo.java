package com.lhkeeper.ticketing.railway_ticketing.context;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserInfo {

    private Long userId;
    private String username;
    private String phone;
}
