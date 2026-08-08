package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

import java.util.List;

@Data
public class TrainCloneReqDTO {

    private String trainNumber;
    private List<TrainStationReqDTO> stations;
}
