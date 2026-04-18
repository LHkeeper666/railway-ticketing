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
 * 车站表
 * </p>
 *
 * @author jack
 * @since 2026-04-18
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_station")
public class Station extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 车站编号
     */
    @TableField("code")
    private String code;

    /**
     * 车站名称
     */
    @TableField("name")
    private String name;

    /**
     * 拼音
     */
    @TableField("spell")
    private String spell;

    /**
     * 车站地区
     */
    @TableField("region")
    private String region;

    /**
     * 车站地区名称
     */
    @TableField("region_name")
    private String regionName;
}
