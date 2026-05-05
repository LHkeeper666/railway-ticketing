package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 抢票请求响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashOrderCreateRespDTO {

    /** 订单号 */
    private String orderSn;

    /** 排队提示信息 */
    private String message;
}
