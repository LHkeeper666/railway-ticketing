package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRespDTO {

    private String username;
    private String realName;
    private String phone;
    private Integer idType;
    private String idCard;
    private String mail;
    private String region;
    private String address;
    private Integer userType;
    private Integer verifyStatus;
    private String telephone;
    private String postCode;
}
