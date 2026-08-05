package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

@Data
public class PassengerCreateReqDTO {

    private String realName;
    private Integer idType;
    private String idCard;
    private String phone;
    private Integer discountType;
}
