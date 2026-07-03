package com.lhkeeper.ticketing.railway_ticketing.service;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.WaitlistCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.WaitlistCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.WaitlistDetailRespDTO;

public interface WaitlistService {

    WaitlistCreateRespDTO createWaitlist(WaitlistCreateReqDTO reqDTO);

    void processWaitlist(Long trainId, Integer seatType, String startStation, String endStation);

    void cancelWaitlist(String waitlistSn);

    WaitlistDetailRespDTO getWaitlistDetail(String waitlistSn);

    void triggerMatch(Long trainId, String startStation, String endStation);

    void processMatch(Long trainId, String startStation, String endStation);

    void cleanUpWaitlistByOrderSn(String orderSn);
}
