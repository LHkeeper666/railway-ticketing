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
 * 订单明细表
 * </p>
 *
 * @author jack
 * @since 2026-04-21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order_item")
public class OrderItem extends BaseEntity {

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
     * 车厢号
     */
    @TableField("carriage_number")
    private String carriageNumber;

    /**
     * 座位类型
     */
    @TableField("seat_type")
    private Integer seatType;

    /**
     * 座位号
     */
    @TableField("seat_number")
    private String seatNumber;

    /**
     * 真实姓名
     */
    @TableField("real_name")
    private String realName;

    /**
     * 证件类型
     */
    @TableField("id_type")
    private Integer idType;

    /**
     * 证件号
     */
    @TableField("id_card")
    private String idCard;

    /**
     * 车票类型
     */
    @TableField("ticket_type")
    private Integer ticketType;

    /**
     * 手机号
     */
    @TableField("phone")
    private String phone;

    /**
     * 订单状态
     */
    @TableField("status")
    private Integer status;

    /**
     * 订单金额
     */
    @TableField("amount")
    private Integer amount;
}
