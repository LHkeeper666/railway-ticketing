package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

@Data
public class PassengerUpdateReqDTO {

    private String phone;
    private Integer discountType;
}
