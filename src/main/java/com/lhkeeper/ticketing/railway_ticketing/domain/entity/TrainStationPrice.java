package com.lhkeeper.ticketing.railway_ticketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.BaseEntity;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 列车站点价格实体，记录不同座位类型在各区间的票价
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
    @TableField("start_station")
    private String startStation;

    /**
     * 到达站点
     */
    @TableField("end_station")
    private String endStation;

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
