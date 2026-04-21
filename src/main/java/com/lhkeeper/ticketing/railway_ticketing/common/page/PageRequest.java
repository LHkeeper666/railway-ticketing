package com.lhkeeper.ticketing.railway_ticketing.common.page;

import lombok.Data;

@Data
public class PageRequest {

    /**
     * 当前页
     */
    private Long current = 1L;

    /**
     * 每页显示条数
     */
    private Long size = 10L;
}
