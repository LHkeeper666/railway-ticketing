package com.lhkeeper.ticketing.railway_ticketing.domain.dto;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashOrderMessageDTO {

    private String orderSn;

    private Long trainId;

    private String startStation;

    private String endStation;

    private List<OrderCreatePassengerDetailDTO> passengers;
}
