package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TrainStationReqDTO {

    private String stationName;
    private String regionName;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer stopoverTime;
}
