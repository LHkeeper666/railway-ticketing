package com.lhkeeper.ticketing.railway_ticketing.service;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.ChangeReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.ChangeRespDTO;

public interface ChangeService {

    ChangeRespDTO change(ChangeReqDTO reqDTO);
}
