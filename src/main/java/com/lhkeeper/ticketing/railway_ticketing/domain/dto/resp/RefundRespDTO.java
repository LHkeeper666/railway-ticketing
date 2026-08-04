package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRespDTO {

    private String refundSn;
    private String orderSn;
    private BigDecimal refundAmount;
    private BigDecimal feeAmount;
    private Integer refundTicketCount;
    private String status;
}
