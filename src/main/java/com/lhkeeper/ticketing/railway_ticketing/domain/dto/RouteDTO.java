package com.lhkeeper.ticketing.railway_ticketing.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 列车区间
 */
@Data
@AllArgsConstructor
public class RouteDTO {

    /** 出发站点 */
    private String startStation;
    /** 到达站点 */
    private String endStation;
}
