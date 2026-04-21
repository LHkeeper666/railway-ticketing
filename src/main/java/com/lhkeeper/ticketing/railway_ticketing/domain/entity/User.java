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
 * 用户表
 * </p>
 *
 * @author jack
 * @since 2026-04-21
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_user")
public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;

    /**
     * 密码
     */
    @TableField("password")
    private String password;

    /**
     * 真实姓名
     */
    @TableField("real_name")
    private String realName;

    /**
     * 国家/地区
     */
    @TableField("region")
    private String region;

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
     * 手机号
     */
    @TableField("phone")
    private String phone;

    /**
     * 固定电话
     */
    @TableField("telephone")
    private String telephone;

    /**
     * 邮箱
     */
    @TableField("mail")
    private String mail;

    /**
     * 旅客类型
     */
    @TableField("user_type")
    private Integer userType;

    /**
     * 审核状态
     */
    @TableField("verify_status")
    private Integer verifyStatus;

    /**
     * 邮编
     */
    @TableField("post_code")
    private String postCode;

    /**
     * 地址
     */
    @TableField("address")
    private String address;

    /**
     * 注销时间戳
     */
    @TableField("deletion_time")
    private Long deletionTime;
}
