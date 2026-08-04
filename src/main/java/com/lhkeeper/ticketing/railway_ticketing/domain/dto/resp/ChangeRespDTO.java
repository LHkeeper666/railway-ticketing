package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRespDTO {

    private String changeSn;
    private String orderSn;
    private BigDecimal priceDiff;
    private BigDecimal feeAmount;
    private Integer changeTicketCount;
    private String status;
    /** 新生成的 ticket ID 列表 */
    private List<Long> newTicketIds;
}
