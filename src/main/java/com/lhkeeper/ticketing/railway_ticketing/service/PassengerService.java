package com.lhkeeper.ticketing.railway_ticketing.service;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerUpdateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.PassengerRespDTO;

import java.util.List;

public interface PassengerService {

    PassengerRespDTO create(PassengerCreateReqDTO reqDTO);

    PassengerRespDTO update(Long passengerId, PassengerUpdateReqDTO reqDTO);

    void delete(Long passengerId);

    List<PassengerRespDTO> listMine();
}
