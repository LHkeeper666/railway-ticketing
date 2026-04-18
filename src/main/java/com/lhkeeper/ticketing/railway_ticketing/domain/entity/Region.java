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
 * 地区表
 * </p>
 *
 * @author jack
 * @since 2026-04-18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_region")
public class Region extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 地区名称
     */
    @TableField("name")
    private String name;

    /**
     * 地区全名
     */
    @TableField("full_name")
    private String fullName;

    /**
     * 地区编码
     */
    @TableField("code")
    private String code;

    /**
     * 地区首字母
     */
    @TableField("initial")
    private String initial;

    /**
     * 拼音
     */
    @TableField("spell")
    private String spell;
}
