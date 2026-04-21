package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import java.util.Date;
import org.springframework.format.annotation.DateTimeFormat;
import com.lhkeeper.ticketing.railway_ticketing.common.page.PageRequest;
import lombok.Data;

/**
 * 车票分页查询请求参数
 */
@Data
public class TicketPageQueryReqDTO extends PageRequest {

    /**
     * 出发地 Code
     */
    private String startRegionCode;

    /**
     * 目的地 Code
     */
    private String endRegionCode;

    /**
     * 出发日期
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date departureDate;

    // /**
    //  * 出发站点
    //  */
    // private String departure;

    // /**
    //  * 到达站点
    //  */
    // private String arrival;
}

