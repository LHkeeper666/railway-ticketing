package com.lhkeeper.ticketing.railway_ticketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.BaseEntity;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 * 座位表
 * </p>
 *
 * @author jack
 * @since 2026-04-18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_seat")
public class Seat extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 列车ID
     */
    @TableField("train_id")
    private Long trainId;

    /**
     * 车厢号
     */
    @TableField("carriage_number")
    private String carriageNumber;

    /**
     * 座位号
     */
    @TableField("seat_number")
    private String seatNumber;

    /**
     * 座位类型
     */
    @TableField("seat_type")
    private Integer seatType;

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
     * 车票价格
     */
    @TableField("price")
    private Integer price;

    /**
     * 座位状态
     */
    @TableField("seat_status")
    private Integer seatStatus;
}
