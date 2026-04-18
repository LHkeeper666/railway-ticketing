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
 * 列车站点关系表
 * </p>
 *
 * @author jack
 * @since 2026-04-18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_train_station_relation")
public class TrainStationRelation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 车次ID
     */
    @TableField("train_id")
    private Long trainId;

    /**
     * 出发站点
     */
    @TableField("departure")
    private String departure;

    /**
     * 到达站点
     */
    @TableField("arrival")
    private String arrival;

    /**
     * 起始城市名称
     */
    @TableField("start_region")
    private String startRegion;

    /**
     * 终点城市名称
     */
    @TableField("end_region")
    private String endRegion;

    /**
     * 始发标识
     */
    @TableField("departure_flag")
    private Boolean departureFlag;

    /**
     * 终点标识
     */
    @TableField("arrival_flag")
    private Boolean arrivalFlag;

    /**
     * 出发时间
     */
    @TableField("departure_time")
    private LocalDateTime departureTime;

    /**
     * 到达时间
     */
    @TableField("arrival_time")
    private LocalDateTime arrivalTime;
}
