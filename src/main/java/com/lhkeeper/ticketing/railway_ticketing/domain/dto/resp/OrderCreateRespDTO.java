package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderItemDTO;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 订单创建请求响应实体
 */
@Data
@Builder
public class OrderCreateRespDTO {

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 乘车人车票详情
     */
    private List<OrderItemDTO> orderItemDTOS;
}
