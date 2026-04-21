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
 * 列车站点价格表
 * </p>
 *
 * @author jack
 * @since 2026-04-21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_train_station_price")
public class TrainStationPrice extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 车次ID
     */
    @TableField("train_id")
    private Long trainId;

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
     * 座位类型
     */
    @TableField("seat_type")
    private Integer seatType;

    /**
     * 车票价格
     */
    @TableField("price")
    private Integer price;
}
