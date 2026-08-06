package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

@Data
public class UserUpdateReqDTO {

    private String realName;
    private Integer idType;
    private String idCard;
    private String mail;
    private String region;
    private String address;
    private String telephone;
    private String postCode;
    private Integer userType;
}
