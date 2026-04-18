package com.lhkeeper.ticketing.railway_ticketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.BaseEntity;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 * 乘车人表
 * </p>
 *
 * @author jack
 * @since 2026-04-18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_passenger")
public class Passenger extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;

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
     * 证件号码
     */
    @TableField("id_card")
    private String idCard;

    /**
     * 优惠类型
     */
    @TableField("discount_type")
    private Integer discountType;

    /**
     * 手机号
     */
    @TableField("phone")
    private String phone;

    /**
     * 添加日期
     */
    @TableField("create_date")
    private LocalDateTime createDate;

    /**
     * 审核状态
     */
    @TableField("verify_status")
    private Integer verifyStatus;
}
