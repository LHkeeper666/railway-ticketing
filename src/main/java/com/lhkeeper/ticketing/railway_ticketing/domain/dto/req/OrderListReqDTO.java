package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import com.lhkeeper.ticketing.railway_ticketing.common.page.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
public class OrderListReqDTO extends PageRequest {

    private Integer status;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    private String trainNumber;
}
