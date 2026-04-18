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
 * 列车表
 * </p>
 *
 * @author jack
 * @since 2026-04-18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_train")
public class Train extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 列车车次
     */
    @TableField("train_number")
    private String trainNumber;

    /**
     * 列车类型 0：高铁 1：动车 2：普通车
     */
    @TableField("train_type")
    private Integer trainType;

    /**
     * 列车标签 0：复兴号 1：智能动车组 2：静音车厢 3：支持选铺
     */
    @TableField("train_tag")
    private String trainTag;

    /**
     * 列车品牌 0：GC-高铁/城际 1：D-动车 2：Z-直达 3：T-特快 4：K-快速 5：其他 6：复兴号 7：智能动车组
     */
    @TableField("train_brand")
    private String trainBrand;

    /**
     * 起始站
     */
    @TableField("start_station")
    private String startStation;

    /**
     * 终点站
     */
    @TableField("end_station")
    private String endStation;

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
     * 销售时间
     */
    @TableField("sale_time")
    private LocalDateTime saleTime;

    /**
     * 销售状态 0：可售 1：不可售 2：未知
     */
    @TableField("sale_status")
    private Integer saleStatus;

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
