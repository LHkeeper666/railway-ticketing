package com.lhkeeper.ticketing.railway_ticketing.domain.dto;

import lombok.Data;

/**
 * 购票乘车人详情实体
 */
@Data
public class OrderCreatePassengerDetailDTO {

    /**
     * 乘车人 ID
     */
    private String passengerId;

    /**
     * 座位类型
     */
    private Integer seatType;
}
