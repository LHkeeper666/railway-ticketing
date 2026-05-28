package com.lhkeeper.ticketing.railway_ticketing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStation;

import java.util.List;

public interface TrainStationService extends IService<TrainStation> {

    List<TrainStation> getTrainStationsByTrainId(Long trainId);
}
