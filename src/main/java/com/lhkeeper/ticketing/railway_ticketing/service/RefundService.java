package com.lhkeeper.ticketing.railway_ticketing.service;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.RefundRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RefundReqDTO;

public interface RefundService {

    RefundRespDTO refund(RefundReqDTO reqDTO);
}
