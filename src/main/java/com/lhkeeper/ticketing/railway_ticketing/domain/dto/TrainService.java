package com.lhkeeper.ticketing.railway_ticketing.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainService {

    private static final long serialVersionUID = 1L;

    /**
     * 车次ID
     */
    private Long trainId;

    /**
     * 车次
     */
    private String trainNumber;

    /**
     * 出发站点
     */
    private String startStation;

    /**
     * 到达站点
     */
    private String endStation;

    /**
     * 起始城市名称
     */
    private String startRegion;

    /**
     * 终点城市名称
     */
    private String endRegion;

    /**
     * 始发标识
     */
    private Boolean departureFlag;

    /**
     * 终点标识
     */
    private Boolean arrivalFlag;

    /**
     * 出发时间
     */
    private LocalDateTime departureTime;

    /**
     * 到达时间
     */
    private LocalDateTime arrivalTime;

    /**
     * 起售时间
     */
    private LocalDateTime saleTime;

    /**
     * 销售状态 0：可售 1：不可售 2：未知
     */
    private Integer saleStatus;

    /**
     * 列车标签集合 0：复兴号 1：智能动车组 2：静音车厢 3：支持选铺
     */
    private List<String> trainTags;

    /**
     * 列车品牌类型 0：GC-高铁/城际 1：D-动车 2：Z-直达 3：T-特快 4：K-快速 5：其他 6：复兴号 7：智能动车组
     */
    private String trainBrand;

    /**
     * 席别实体集合
     */
    private List<SeatClassDTO> seatClassList;

}
