package com.lhkeeper.ticketing.railway_ticketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.BaseEntity;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 * 订单表
 * </p>
 *
 * @author jack
 * @since 2026-04-18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order")
public class Order extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 订单号
     */
    @TableField("order_sn")
    private String orderSn;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;

    /**
     * 列车ID
     */
    @TableField("train_id")
    private Long trainId;

    /**
     * 列车车次
     */
    @TableField("train_number")
    private String trainNumber;

    /**
     * 乘车日期
     */
    @TableField("riding_date")
    private LocalDate ridingDate;

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
     * 出发时间
     */
    @TableField("departure_time")
    private LocalDateTime departureTime;

    /**
     * 到达时间
     */
    @TableField("arrival_time")
    private LocalDateTime arrivalTime;

    /**
     * 订单状态
     */
    @TableField("status")
    private Integer status;

    /**
     * 下单时间
     */
    @TableField("order_time")
    private LocalDateTime orderTime;

    /**
     * 支付时间
     */
    @TableField("pay_time")
    private LocalDateTime payTime;
}
