package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

@Data
public class ChangePasswordReqDTO {

    private String oldPassword;
    private String newPassword;
    private String confirmPassword;
}
