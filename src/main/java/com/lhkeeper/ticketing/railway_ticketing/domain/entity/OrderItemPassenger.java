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
 * 乘车人订单关系表
 * </p>
 *
 * @author jack
 * @since 2026-04-21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order_item_passenger")
public class OrderItemPassenger extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 订单号
     */
    @TableField("order_sn")
    private String orderSn;

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
}
