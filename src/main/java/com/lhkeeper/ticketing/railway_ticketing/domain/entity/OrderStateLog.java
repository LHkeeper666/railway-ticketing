package com.lhkeeper.ticketing.railway_ticketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单状态变更日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_order_state_log")
public class OrderStateLog extends BaseEntity {

    @TableField("order_sn")
    private String orderSn;

    @TableField("from_status")
    private Integer fromStatus;

    @TableField("to_status")
    private Integer toStatus;

    @TableField("event")
    private String event;

    @TableField("operator")
    private String operator;

    @TableField("remark")
    private String remark;
}
