package com.lhkeeper.ticketing.railway_ticketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.BaseEntity;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 * 列车区间表
 * </p>
 *
 * @author jack
 * @since 2026-04-21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_train_station")
public class TrainStation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 车次ID
     */
    @TableField("train_id")
    private Long trainId;

    /**
     * 车站ID
     */
    @TableField("station_id")
    private Long stationId;

    /**
     * 站点顺序
     */
    @TableField("sequence")
    private String sequence;

    /**
     * 出发站点
     */
    @TableField("departure_station")
    private String departureStation;

    /**
     * 到达站点
     */
    @TableField("arrival_station")
    private String arrivalStation;

    /**
     * 起始城市
     */
    @TableField("start_region")
    private String startRegion;

    /**
     * 终点城市
     */
    @TableField("end_region")
    private String endRegion;

    /**
     * 到站时间
     */
    @TableField("arrival_time")
    private LocalDateTime arrivalTime;

    /**
     * 出站时间
     */
    @TableField("departure_time")
    private LocalDateTime departureTime;

    /**
     * 停留时间，单位分
     */
    @TableField("stopover_time")
    private Integer stopoverTime;
}
