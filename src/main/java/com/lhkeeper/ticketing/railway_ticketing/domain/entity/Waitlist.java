package com.lhkeeper.ticketing.railway_ticketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_waitlist")
public class Waitlist extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("waitlist_sn")
    private String waitlistSn;

    @TableField("order_sn")
    private String orderSn;

    @TableField("user_id")
    private Long userId;

    @TableField("train_id")
    private Long trainId;

    @TableField("start_station")
    private String startStation;

    @TableField("end_station")
    private String endStation;

    @TableField("seat_type")
    private Integer seatType;

    @TableField("passenger_count")
    private Integer passengerCount;

    /** 0=WAITING, 1=MATCHED, 2=EXPIRED, 3=CANCELED */
    @TableField("status")
    private Integer status;

    @TableField("expire_time")
    private LocalDateTime expireTime;
}
