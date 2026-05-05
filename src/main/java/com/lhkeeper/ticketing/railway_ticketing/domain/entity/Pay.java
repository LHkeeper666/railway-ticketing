package com.lhkeeper.ticketing.railway_ticketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.BaseEntity;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 支付记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_pay")
public class Pay extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 支付流水号
     */
    @TableField("pay_sn")
    private String paySn;

    /**
     * 订单号
     */
    @TableField("order_sn")
    private String orderSn;

    /**
     * 商户订单号
     */
    @TableField("out_order_sn")
    private String outOrderSn;

    /**
     * 支付渠道
     */
    @TableField("channel")
    private String channel;

    /**
     * 支付环境
     */
    @TableField("trade_type")
    private String tradeType;

    /**
     * 订单标题
     */
    @TableField("subject")
    private String subject;

    /**
     * 商户订单号
     */
    @TableField("order_request_id")
    private String orderRequestId;

    /**
     * 交易总金额
     */
    @TableField("total_amount")
    private BigDecimal totalAmount;

    /**
     * 三方交易凭证号
     */
    @TableField("trade_no")
    private String tradeNo;

    /**
     * 付款时间
     */
    @TableField("gmt_payment")
    private LocalDateTime gmtPayment;

    /**
     * 支付金额
     */
    @TableField("pay_amount")
    private Integer payAmount;

    /**
     * 支付状态
     */
    @TableField("status")
    private String status;
}
