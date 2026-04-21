package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import java.util.List;

import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStationRelation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketPageQueryRespDTO {
    
    /**
     * 车次集合数据
     */
    // private List<TicketListDTO> trainList;

    /**
     * 车次类型：D-动车 Z-直达 复兴号等
     */
    // private List<Integer> trainBrandList;

    /**
     * 出发车站
     */
    // private List<String> startStationList;

    /**
     * 到达车站
     */
    // private List<String> endStationList;

    private List<TrainStationRelation> trainList;

    /**
     * 车次席别
     */
    // private List<Integer> seatClassTypeList;
}
