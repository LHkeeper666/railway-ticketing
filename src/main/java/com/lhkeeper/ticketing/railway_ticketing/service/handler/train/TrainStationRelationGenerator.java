package com.lhkeeper.ticketing.railway_ticketing.service.handler.train;

import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStation;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStationRelation;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationRelationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 列车站点关系自动生成器。
 * 根据有序 TrainStation 列表生成所有可能的起止站直达关系 C(n,2)。
 */
@Component
@RequiredArgsConstructor
public class TrainStationRelationGenerator {

    private final TrainStationRelationMapper trainStationRelationMapper;

    /**
     * 根据有序站点列表，全量重建 t_train_station_relation。
     * 先删除该车次所有旧关系，再插入新的。
     *
     * @param trainId  列车 ID
     * @param stations 有序站点列表（按 sequence 排序，包含终端站记录）
     */
    public void regenerate(Long trainId, List<TrainStation> stations) {
        // delete old
        trainStationRelationMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TrainStationRelation>()
                        .eq(TrainStationRelation::getTrainId, trainId)
        );

        int n = stations.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                TrainStation from = stations.get(i);
                TrainStation to = stations.get(j);

                TrainStationRelation relation = new TrainStationRelation();
                relation.setTrainId(trainId);
                relation.setStartStation(from.getStartStation());
                relation.setEndStation(to.getEndStation() != null ? to.getEndStation() : to.getStartStation());
                relation.setStartRegion(from.getStartRegion());
                relation.setEndRegion(to.getEndRegion() != null ? to.getEndRegion() : to.getStartRegion());
                relation.setDepartureFlag(i == 0);
                relation.setArrivalFlag(j == n - 1);
                relation.setDepartureTime(from.getDepartureTime());
                relation.setArrivalTime(to.getArrivalTime());

                trainStationRelationMapper.insert(relation);
            }
        }
    }
}
