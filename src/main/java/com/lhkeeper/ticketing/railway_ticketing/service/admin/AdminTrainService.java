package com.lhkeeper.ticketing.railway_ticketing.service.admin;

import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;

import java.util.List;

public interface AdminTrainService {

    PageResponse<Train> page(long current, long size, String trainNumber, Integer trainType);

    Train getDetail(Long id);

    List<TrainStation> getStations(Long id);

    Train create(Train train);

    Train updateMeta(Long id, Train train);

    void delete(Long id);

    void setStations(Long trainId, List<TrainStationReqDTO> stationDTOs);

    void appendStation(Long trainId, TrainStationReqDTO dto);

    void insertStation(Long trainId, int index, TrainStationReqDTO dto);

    void deleteStation(Long trainId, Long stationId);

    Train clone(Long sourceTrainId, TrainCloneReqDTO reqDTO);

    List<Carriage> getCarriages(Long trainId);

    Carriage addCarriage(Long trainId, TrainCarriageReqDTO dto);

    void deleteCarriage(Long trainId, Long carriageId);

    List<Seat> getSeats(Long trainId, String carriageNumber);

    void deleteSeat(Long trainId, Long seatId);

    List<TrainStationPrice> getPrices(Long trainId);

    void batchUpdatePrices(Long trainId, TrainPriceBatchReqDTO reqDTO);

    void delay(Long trainId, TrainDelayReqDTO reqDTO);
}
