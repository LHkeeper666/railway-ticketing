package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 模拟支付页面展示 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockPayPageDTO {

    private String paySn;
    private String orderSn;
    private BigDecimal totalAmount;
    private String status;
    private String subject;
}
