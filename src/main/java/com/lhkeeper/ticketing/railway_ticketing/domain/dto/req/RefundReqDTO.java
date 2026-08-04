package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundReqDTO {

    private String orderSn;

    /** 要退票的 ticket ID 列表，支持部分退票 */
    private List<Long> ticketIds;

    /** 退款原因（选填） */
    private String reason;
}
