package com.lhkeeper.ticketing.railway_ticketing.domain.dto;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 抢票消息体，投递到 RabbitMQ 的订单信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashOrderMessageDTO {

    /** 订单号 */
    private String orderSn;

    /** 列车ID */
    private Long trainId;

    /** 出发站点 */
    private String startStation;

    /** 到达站点 */
    private String endStation;

    /** 乘车人列表 */
    private List<OrderCreatePassengerDetailDTO> passengers;

    /** 选座偏好 */
    private List<String> chooseSeats;
}
