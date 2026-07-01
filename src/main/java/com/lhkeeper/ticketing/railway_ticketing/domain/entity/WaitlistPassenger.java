package com.lhkeeper.ticketing.railway_ticketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_waitlist_passenger")
public class WaitlistPassenger extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("waitlist_id")
    private Long waitlistId;

    @TableField("passenger_id")
    private Long passengerId;

    /** 选座偏好 (JSON) */
    @TableField("seat_preference")
    private String seatPreference;
}
