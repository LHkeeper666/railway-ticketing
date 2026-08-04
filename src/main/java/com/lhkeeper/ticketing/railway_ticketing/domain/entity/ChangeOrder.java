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
 * 改签记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_change_order")
public class ChangeOrder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableField("change_sn")
    private String changeSn;

    @TableField("order_sn")
    private String orderSn;

    @TableField("old_train_id")
    private Long oldTrainId;

    @TableField("new_train_id")
    private Long newTrainId;

    @TableField("old_start_station")
    private String oldStartStation;

    @TableField("old_end_station")
    private String oldEndStation;

    @TableField("new_start_station")
    private String newStartStation;

    @TableField("new_end_station")
    private String newEndStation;

    @TableField("old_amount")
    private BigDecimal oldAmount;

    @TableField("new_amount")
    private BigDecimal newAmount;

    @TableField("price_diff")
    private BigDecimal priceDiff;

    @TableField("fee_amount")
    private BigDecimal feeAmount;

    @TableField("change_ticket_count")
    private Integer changeTicketCount;

    @TableField("status")
    private String status;

    @TableField("old_departure_time")
    private LocalDateTime oldDepartureTime;

    @TableField("new_departure_time")
    private LocalDateTime newDepartureTime;
}
