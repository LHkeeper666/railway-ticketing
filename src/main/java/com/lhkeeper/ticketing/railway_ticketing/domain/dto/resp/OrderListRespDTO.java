package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderListRespDTO {

    private String orderSn;
    private String trainNumber;
    private LocalDate ridingDate;
    private String startStation;
    private String endStation;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer status;
    private LocalDateTime orderTime;
    private BigDecimal totalAmount;
    private Integer passengerCount;
}
