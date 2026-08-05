CREATE TABLE `t_carriage`
(
    `id`              bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `train_id`        bigint(20) DEFAULT NULL COMMENT '列车ID',
    `carriage_number` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '车厢号',
    `carriage_type`   int(3) DEFAULT NULL COMMENT '车厢类型',
    `seat_count`      int(3) DEFAULT NULL COMMENT '座位数',
    `create_time`     datetime                               DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime                               DEFAULT NULL COMMENT '修改时间',
    `del_flag`        tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY               `idx_train_id` (`train_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=65 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车厢表';

CREATE TABLE `t_region`
(
    `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `name`         varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地区名称',
    `full_name`    varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地区全名',
    `code`         varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地区编码',
    `initial`      varchar(2) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '地区首字母',
    `spell`        varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '拼音',
    `create_time`  datetime                               DEFAULT NULL COMMENT '创建时间',
    `update_time`  datetime                               DEFAULT NULL COMMENT '修改时间',
    `del_flag`     tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='地区表';

CREATE TABLE `t_seat`
(
    `id`              bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `train_id`        bigint(20) DEFAULT NULL COMMENT '列车ID',
    `carriage_number` varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '车厢号',
    `seat_number`     varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '座位号',
    `seat_type`       int(3) DEFAULT NULL COMMENT '座位类型',
    `seat_bitmap`     bigint(20) DEFAULT 0 COMMENT '座位占用位图，每bit对应一个相邻站点段',
    `price`           int(11) DEFAULT NULL COMMENT '车票价格（基础价格，实际价格查t_train_station_price）',
    `seat_status`     int(3) DEFAULT NULL COMMENT '座位状态',
    `create_time`     datetime                                DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime                                DEFAULT NULL COMMENT '修改时间',
    `del_flag`        tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY               `idx_train_type` (`train_id`, `seat_type`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1683022080920494081 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='座位表';

CREATE TABLE `t_station`
(
    `id`          bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `station_code`        varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '车站编号',
    `station_name`        varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '车站名称',
    `spell`       varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '拼音',
    `region_code`      varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '车站地区',
    `region_name` varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '车站地区名称',
    `create_time` datetime                                DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime                                DEFAULT NULL COMMENT '修改时间',
    `del_flag`    tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车站表';

CREATE TABLE `t_ticket`
(
    `id`              bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `order_sn`        varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '订单号',
    `username`        varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户名',
    `train_id`        bigint(20) DEFAULT NULL COMMENT '列车ID',
    `carriage_number` varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '车厢号',
    `seat_number`     varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '座位号',
    `passenger_id`    bigint(20) DEFAULT NULL COMMENT '乘车人ID',
    `ticket_status`   int(3) DEFAULT NULL COMMENT '车票状态',
    `purchase_mask`   bigint(20) DEFAULT NULL COMMENT '购买区间位图掩码，用于取消/退票/改签精确释放座位',
    `create_time`     datetime                                DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime                                DEFAULT NULL COMMENT '修改时间',
    `del_flag`        tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1682790903965503489 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车票表';

CREATE TABLE `t_train`
(
    `id`             bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `train_number`   varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列车车次',
    `train_type`     int(3) DEFAULT NULL COMMENT '列车类型 0：高铁 1：动车 2：普通车',
    `train_tag`      varchar(32) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '列车标签 0：复兴号 1：智能动车组 2：静音车厢 3：支持选铺',
    `train_brand`    varchar(32) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '列车品牌 0：GC-高铁/城际 1：D-动车 2：Z-直达 3：T-特快 4：K-快速 5：其他 6：复兴号 7：智能动车组',
    `start_station`  varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '起始站',
    `end_station`    varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '终点站',
    `start_region`   varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '起始城市',
    `end_region`     varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '终点城市',
    `sale_time`      datetime                                DEFAULT NULL COMMENT '销售时间',
    `sale_status`    int(3) DEFAULT NULL COMMENT '销售状态 0：可售 1：不可售 2：未知',
    `departure_time` datetime                                DEFAULT NULL COMMENT '出发时间',
    `arrival_time`   datetime                                DEFAULT NULL COMMENT '到达时间',
    `create_time`    datetime                                DEFAULT NULL COMMENT '创建时间',
    `update_time`    datetime                                DEFAULT NULL COMMENT '修改时间',
    `del_flag`       tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列车表';

CREATE TABLE `t_train_station`
(
    `id`             bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `train_id`       bigint(20) DEFAULT NULL COMMENT '车次ID',
    `station_id`     bigint(20) DEFAULT NULL COMMENT '车站ID',
    `sequence`       varchar(32) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '站点顺序',
    `start_station`      varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '出发站点',
    `end_station`        varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '到达站点',
    `start_region`   varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '起始城市',
    `end_region`     varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '终点城市',
    `departure_time` datetime                                DEFAULT NULL COMMENT '出站时间',
    `arrival_time`   datetime                                DEFAULT NULL COMMENT '到站时间',
    `stopover_time`  int(3) DEFAULT NULL COMMENT '停留时间，单位分',
    `create_time`    datetime                                DEFAULT NULL COMMENT '创建时间',
    `update_time`    datetime                                DEFAULT NULL COMMENT '修改时间',
    `del_flag`       tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY              `idx_train_id` (`train_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列车区间表';

CREATE TABLE `t_train_station_relation`
(
    `id`             bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `train_id`       bigint(20) DEFAULT NULL COMMENT '车次ID',
    `start_station`      varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '出发站点',
    `end_station`        varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '到达站点',
    `start_region`   varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '起始城市名称',
    `end_region`     varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '终点城市名称',
    `departure_flag` tinyint(1) DEFAULT NULL COMMENT '始发标识',
    `arrival_flag`   tinyint(1) DEFAULT NULL COMMENT '终点标识',
    `departure_time` datetime                                DEFAULT NULL COMMENT '出发时间',
    `arrival_time`   datetime                                DEFAULT NULL COMMENT '到达时间',
    `create_time`    datetime                                DEFAULT NULL COMMENT '创建时间',
    `update_time`    datetime                                DEFAULT NULL COMMENT '修改时间',
    `del_flag`       tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY              `idx_train_id` (`train_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1677689610742865921 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列车站点关系表';

CREATE TABLE `t_train_station_price`
(
    `id`          bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `train_id`    bigint(20) DEFAULT NULL COMMENT '车次ID',
    `start_station`   varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '出发站点',
    `end_station`     varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '到达站点',
    `seat_type`   int(3) DEFAULT NULL COMMENT '座位类型',
    `price`       int(11) DEFAULT NULL COMMENT '车票价格',
    `create_time` datetime                               DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime                               DEFAULT NULL COMMENT '修改时间',
    `del_flag`    tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY           `idx_train_id` (`train_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1677692017354547201 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='列车站点价格表';

CREATE TABLE `t_order`
(
    `id`             bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `order_sn`       varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '订单号',
    `user_id`        bigint(20) DEFAULT NULL COMMENT '用户ID',
    `username`       varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户名',
    `train_id`       bigint(20) DEFAULT NULL COMMENT '列车ID',
    `train_number`   varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '列车车次',
    `riding_date`    date                                    DEFAULT NULL COMMENT '乘车日期',
    `start_station`      varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '出发站点',
    `end_station`        varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '到达站点',
    `departure_time` datetime                                DEFAULT NULL COMMENT '出发时间',
    `arrival_time`   datetime                                DEFAULT NULL COMMENT '到达时间',
    -- TODO: 不是很理解
    -- `source`         int(3) DEFAULT NULL COMMENT '订单来源',
    `status`         int(3) DEFAULT NULL COMMENT '订单状态',
    `order_time`     datetime                                DEFAULT NULL COMMENT '下单时间',
    -- `pay_type`       int(3) DEFAULT NULL COMMENT '支付方式',
    `pay_time`       datetime                                DEFAULT NULL COMMENT '支付时间',
    `create_time`    datetime                                DEFAULT NULL COMMENT '创建时间',
    `update_time`    datetime                                DEFAULT NULL COMMENT '修改时间',
    `del_flag`       tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY              `idx_user_id` (`user_id`) USING BTREE,
    KEY              `idx_order_sn` (`order_sn`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

CREATE TABLE `t_order_item`
(
    `id`              bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `order_sn`        varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '订单号',
    `user_id`         bigint(20) DEFAULT NULL COMMENT '用户ID',
    `username`        varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户名',
    `train_id`        bigint(20) DEFAULT NULL COMMENT '列车ID',
    `carriage_number` varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '车厢号',
    `seat_type`       int(3) DEFAULT NULL COMMENT '座位类型',
    `seat_number`     varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '座位号',
    `real_name`       varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '真实姓名',
    -- TODO: 似乎不只有3种
    `id_type`         int(3) DEFAULT NULL COMMENT '证件类型',
    `id_card`         varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '证件号',
    `ticket_type`     int(3) DEFAULT NULL COMMENT '车票类型',
    `phone`           varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
    `status`          int(3) DEFAULT NULL COMMENT '订单状态',
    `amount`          int(11) DEFAULT NULL COMMENT '订单金额',
    `create_time`     datetime                                DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime                                DEFAULT NULL COMMENT '修改时间',
    `del_flag`        tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY               `idx_order_sn` (`order_sn`) USING BTREE,
    KEY               `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';

-- 把“乘客维度查询”从订单明细中剥离出来
-- 订单 - 乘客关系
CREATE TABLE `t_order_item_passenger`
(
    `id`          bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `order_sn`    varchar(64) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '订单号',
    `id_type`     int(3) DEFAULT NULL COMMENT '证件类型',
    `id_card`     varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '证件号',
    `create_time` datetime                                DEFAULT NULL COMMENT '创建时间',
    `update_time` datetime                                DEFAULT NULL COMMENT '修改时间',
    `del_flag`    tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY           `idx_id_card` (`id_card`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='乘车人订单关系表';

CREATE TABLE `t_passenger`
(
    `id`            bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `username`      varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '用户名',
    `user_id`       bigint(20) DEFAULT NULL COMMENT '用户ID',
    `real_name`     varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '真实姓名',
    `id_type`       int(3) DEFAULT NULL COMMENT '证件类型',
    `id_card`       varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '证件号码',
    `discount_type` int(3) DEFAULT NULL COMMENT '优惠类型',
    `phone`         varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
    `create_date`   datetime                                DEFAULT NULL COMMENT '添加日期',
    `verify_status` int(3) DEFAULT NULL COMMENT '审核状态',
    `create_time`   datetime                                DEFAULT NULL COMMENT '创建时间',
    `update_time`   datetime                                DEFAULT NULL COMMENT '修改时间',
    `del_flag`      tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY             `idx_id_card` (`id_card`) USING BTREE,
    KEY             `idx_username` (`username`) USING BTREE,
    KEY             `idx_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='乘车人表';

-- 存量数据回填 SQL（新加 user_id 列后执行）:
-- UPDATE t_passenger p
-- INNER JOIN t_user u ON p.username = u.username
-- SET p.user_id = u.id
-- WHERE p.user_id IS NULL;

CREATE TABLE `t_pay`
(
    `id`               bigint(20) NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `pay_sn`           varchar(64)  DEFAULT NULL COMMENT '支付流水号',
    `order_sn`         varchar(64)  DEFAULT NULL COMMENT '订单号',
    `out_order_sn`     varchar(64)  DEFAULT NULL COMMENT '商户订单号',
    `channel`          varchar(64)  DEFAULT NULL COMMENT '支付渠道',
    `trade_type`       varchar(64)  DEFAULT NULL COMMENT '支付环境',
    `subject`          varchar(512) DEFAULT NULL COMMENT '订单标题',
    `order_request_id` varchar(64)  DEFAULT NULL COMMENT '商户订单号',
    `total_amount`     int(11) DEFAULT NULL COMMENT '交易总金额',
    `trade_no`         varchar(256) DEFAULT NULL COMMENT '三方交易凭证号',
    `gmt_payment`      datetime     DEFAULT NULL COMMENT '付款时间',
    `pay_amount`       int(11) DEFAULT NULL COMMENT '支付金额',
    `status`           varchar(32)  DEFAULT NULL COMMENT '支付状态',
    `create_time`      datetime     DEFAULT NULL COMMENT '创建时间',
    `update_time`      datetime     DEFAULT NULL COMMENT '修改时间',
    `del_flag`         tinyint(1) DEFAULT NULL COMMENT '删除标记 0：未删除 1：删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `id` (`id`),
    UNIQUE KEY `uk_order_sn` (`order_sn`),
    KEY                `idx_pay_sn` (`pay_sn`) USING BTREE,
    KEY                `idx_order_sn` (`order_sn`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付表';

CREATE TABLE `t_user`
(
    `id`            bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `username`      varchar(256) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '用户名',
    `password`      varchar(512) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '密码',
    `real_name`     varchar(256) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '真实姓名',
    `region`        varchar(64) COLLATE utf8mb4_unicode_ci   DEFAULT '0' COMMENT '国家/地区',
    `id_type`       int(3) DEFAULT NULL COMMENT '证件类型',
    `id_card`       varchar(256) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '证件号',
    `phone`         varchar(128) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '手机号',
    `telephone`     varchar(128) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '固定电话',
    `mail`          varchar(256) COLLATE utf8mb4_unicode_ci  DEFAULT NULL COMMENT '邮箱',
    `user_type`     int(3) DEFAULT NULL COMMENT '旅客类型',
    `verify_status` int(3) DEFAULT NULL COMMENT '审核状态',
    `post_code`     varchar(64) COLLATE utf8mb4_unicode_ci   DEFAULT NULL COMMENT '邮编',
    `address`       varchar(1024) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '地址',
    `deletion_time` bigint(20) DEFAULT '0' COMMENT '注销时间戳',
    `create_time`   datetime                                 DEFAULT NULL COMMENT '创建时间',
    `update_time`   datetime                                 DEFAULT NULL COMMENT '修改时间',
    `del_flag`      tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_username` (`username`,`deletion_time`) USING BTREE,
    UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `t_waitlist`
(
    `id`              bigint(20) unsigned NOT NULL COMMENT '雪花ID',
    `waitlist_sn`     varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '候补单号',
    `order_sn`        varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联订单号',
    `user_id`         bigint(20) NOT NULL COMMENT '用户ID',
    `train_id`        bigint(20) NOT NULL COMMENT '列车ID',
    `start_station`   varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '出发站',
    `end_station`     varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '到达站',
    `seat_type`       int(3) NOT NULL COMMENT '座位类型',
    `passenger_count` int(3) NOT NULL COMMENT '候补人数',
    `status`          int(3) NOT NULL DEFAULT 0 COMMENT '状态: 0=WAITING, 1=MATCHED, 2=EXPIRED, 3=CANCELED',
    `expire_time`     datetime NOT NULL COMMENT '截止时间',
    `create_time`     datetime DEFAULT NULL COMMENT '创建时间',
    `update_time`     datetime DEFAULT NULL COMMENT '修改时间',
    `del_flag`        tinyint(1) DEFAULT 0 COMMENT '删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_waitlist_sn` (`waitlist_sn`),
    KEY `idx_train_seat_type` (`train_id`, `seat_type`, `start_station`, `end_station`, `status`) USING BTREE,
    KEY `idx_status_expire` (`status`, `expire_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='候补记录表';

CREATE TABLE `t_waitlist_passenger`
(
    `id`              bigint(20) unsigned NOT NULL COMMENT '雪花ID',
    `waitlist_id`     bigint(20) NOT NULL COMMENT '候补记录ID',
    `passenger_id`    bigint(20) NOT NULL COMMENT '乘客ID',
    `seat_preference` varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '选座偏好(JSON)',
    `create_time`     datetime DEFAULT NULL COMMENT '创建时间',
    `del_flag`        tinyint(1) DEFAULT 0 COMMENT '删除标识',
    PRIMARY KEY (`id`),
    KEY `idx_waitlist_id` (`waitlist_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='候补乘客表';

CREATE TABLE `t_refund_order`
(
    `id`                  bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `refund_sn`           varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '退款单号',
    `order_sn`            varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
    `refund_amount`       decimal(10,2) NOT NULL COMMENT '实退金额',
    `fee_amount`          decimal(10,2) NOT NULL COMMENT '手续费',
    `total_amount`        decimal(10,2) NOT NULL COMMENT '票面总金额',
    `refund_ticket_count` int(3) NOT NULL COMMENT '退票张数',
    `status`              varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态: SUCCESS/FAIL',
    `reason`              varchar(256) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '退款原因',
    `departure_time`      datetime DEFAULT NULL COMMENT '冗余列车出发时间，用于手续费审计',
    `create_time`         datetime DEFAULT NULL COMMENT '创建时间',
    `update_time`         datetime DEFAULT NULL COMMENT '修改时间',
    `del_flag`            tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_sn` (`refund_sn`),
    KEY `idx_order_sn` (`order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='退票记录表';

CREATE TABLE `t_change_order`
(
    `id`                  bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `change_sn`           varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '改签单号',
    `order_sn`            varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
    `old_train_id`        bigint(20) NOT NULL COMMENT '原列车ID',
    `new_train_id`        bigint(20) NOT NULL COMMENT '新列车ID',
    `old_start_station`   varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原出发站',
    `old_end_station`     varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原到达站',
    `new_start_station`   varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '新出发站',
    `new_end_station`     varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '新到达站',
    `old_amount`          decimal(10,2) NOT NULL COMMENT '旧票总金额',
    `new_amount`          decimal(10,2) NOT NULL COMMENT '新票总金额',
    `price_diff`          decimal(10,2) NOT NULL COMMENT '价差（正=补差，负=退款）',
    `fee_amount`          decimal(10,2) NOT NULL COMMENT '手续费',
    `change_ticket_count` int(3) NOT NULL COMMENT '改签张数',
    `status`              varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态: COMPLETED/PENDING_PAY/FAILED',
    `old_departure_time`  datetime DEFAULT NULL COMMENT '原列车出发时间',
    `new_departure_time`  datetime DEFAULT NULL COMMENT '新列车出发时间',
    `create_time`         datetime DEFAULT NULL COMMENT '创建时间',
    `update_time`         datetime DEFAULT NULL COMMENT '修改时间',
    `del_flag`            tinyint(1) DEFAULT NULL COMMENT '删除标识',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_change_sn` (`change_sn`),
    KEY `idx_order_sn` (`order_sn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='改签记录表';