package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单乘车人详情
 */
@Data
@Builder
public class OrderItemDetailDTO {

    private String realName;
    private Integer idType;
    private String idCard;
    private Integer ticketType;
    private Integer seatType;
    private String carriageNumber;
    private String seatNumber;
    private BigDecimal amount;
    private Integer status;
    private String phone;
}
