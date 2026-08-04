package com.lhkeeper.ticketing.railway_ticketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 退票记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_refund_order")
public class RefundOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("refund_sn")
    private String refundSn;

    @TableField("order_sn")
    private String orderSn;

    @TableField("refund_amount")
    private BigDecimal refundAmount;

    @TableField("fee_amount")
    private BigDecimal feeAmount;

    @TableField("total_amount")
    private BigDecimal totalAmount;

    @TableField("refund_ticket_count")
    private Integer refundTicketCount;

    @TableField("status")
    private String status;

    @TableField("reason")
    private String reason;

    @TableField("departure_time")
    private LocalDateTime departureTime;
}
