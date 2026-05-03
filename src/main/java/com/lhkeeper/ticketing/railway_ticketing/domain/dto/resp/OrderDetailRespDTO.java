package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单详情响应
 */
@Data
@Builder
public class OrderDetailRespDTO {

    private String orderSn;
    private Long userId;
    private String username;
    private Long trainId;
    private String trainNumber;
    private LocalDate ridingDate;
    private String startStation;
    private String endStation;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer status;
    private LocalDateTime orderTime;
    private LocalDateTime payTime;

    private List<OrderItemDetailDTO> orderItems;
    private PayInfoDTO payInfo;
}
