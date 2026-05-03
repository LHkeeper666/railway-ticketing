package com.lhkeeper.ticketing.railway_ticketing.service;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.LoginReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RegisterReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.LoginRespDTO;

public interface AuthService {

    LoginRespDTO login(LoginReqDTO reqDTO);

    void register(RegisterReqDTO reqDTO);
}
