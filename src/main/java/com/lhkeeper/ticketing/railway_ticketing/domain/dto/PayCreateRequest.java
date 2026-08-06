package com.lhkeeper.ticketing.railway_ticketing.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付创建请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayCreateRequest {

    private String orderSn;
    private String channel;
    private BigDecimal totalAmount;
    private String subject;
}
