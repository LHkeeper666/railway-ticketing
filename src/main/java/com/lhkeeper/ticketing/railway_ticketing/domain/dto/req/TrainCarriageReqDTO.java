package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

@Data
public class TrainCarriageReqDTO {

    private String carriageNumber;
    private Integer carriageType;
    private Integer seatCount;
}
