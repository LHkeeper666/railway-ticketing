package com.lhkeeper.ticketing.railway_ticketing.service.admin;

import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderDetailRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderListRespDTO;

import java.time.LocalDate;

public interface AdminOrderService {

    PageResponse<OrderListRespDTO> page(long current, long size, Integer status, String trainNumber,
                                         Long userId, LocalDate startDate, LocalDate endDate);

    OrderDetailRespDTO getDetail(String orderSn);

    void cancel(String orderSn);

    void refund(String orderSn, String reason);
}
