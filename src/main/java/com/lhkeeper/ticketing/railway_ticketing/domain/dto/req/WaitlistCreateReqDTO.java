package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO;
import lombok.Data;

import java.util.List;

@Data
public class WaitlistCreateReqDTO {

    private String trainId;

    private String startStation;

    private String endStation;

    private Integer seatType;

    private List<OrderCreatePassengerDetailDTO> passengers;
}
