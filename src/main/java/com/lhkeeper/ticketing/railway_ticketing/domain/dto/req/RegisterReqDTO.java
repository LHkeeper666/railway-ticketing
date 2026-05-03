package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

@Data
public class RegisterReqDTO {

    private String phone;
    private String password;
    private String username;
}
