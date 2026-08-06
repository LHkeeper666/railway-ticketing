package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 支付创建结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayCreateResult {

    private String paySn;
    private String orderSn;
    private BigDecimal totalAmount;
    private String payUrl;
    private String sign;
    private Map<String, Object> extra;
}
