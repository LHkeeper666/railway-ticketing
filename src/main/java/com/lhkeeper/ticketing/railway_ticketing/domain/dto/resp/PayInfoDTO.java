package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付信息
 */
@Data
@Builder
public class PayInfoDTO {

    private String paySn;
    private String channel;
    private String tradeNo;
    private BigDecimal totalAmount;
    private String status;
    private LocalDateTime gmtPayment;
}
