package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO;
import lombok.Data;

import java.util.List;

/**
 * 订单创建请求入参
 */
@Data
public class OrderCreateReqDTO {

    /**
     * 车次 ID
     */
    private String trainId;

    /**
     * 乘车人
     */
    private List<OrderCreatePassengerDetailDTO> passengers;

    /**
     * 选择座位
     */
    private List<String> chooseSeats;

    /**
     * 出发站点
     */
    private String startStation;

    /**
     * 到达站点
     */
    private String endStation;
}
