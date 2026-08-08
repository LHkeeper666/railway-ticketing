package com.lhkeeper.ticketing.railway_ticketing.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Station;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStation;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.StationMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.admin.AdminStationService;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminStationServiceImpl implements AdminStationService {

    private final StationMapper stationMapper;
    private final TrainStationMapper trainStationMapper;

    @Override
    public PageResponse<Station> page(long current, long size, String keyword) {
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<Station>()
                .orderByAsc(Station::getSpell);
        if (!StringUtil.isBlank(keyword)) {
            wrapper.and(w -> w.like(Station::getStationName, keyword)
                    .or().like(Station::getStationCode, keyword)
                    .or().like(Station::getSpell, keyword)
                    .or().like(Station::getRegionName, keyword));
        }
        Page<Station> page = new Page<>(current, size);
        IPage<Station> result = stationMapper.selectPage(page, wrapper);
        return PageResponse.from(result, result.getRecords());
    }

    @Override
    public Station getById(Long id) {
        Station station = stationMapper.selectById(id);
        if (station == null) {
            throw new ClientException("站点不存在");
        }
        return station;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Station create(Station station) {
        stationMapper.insert(station);
        return station;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Station update(Long id, Station station) {
        getById(id);
        station.setId(id);
        stationMapper.updateById(station);
        return station;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void delete(Long id) {
        Station station = getById(id);
        long refCount = trainStationMapper.selectCount(
                new LambdaQueryWrapper<TrainStation>()
                        .eq(TrainStation::getStationId, id)
        );
        if (refCount > 0) {
            throw new ClientException("该站点被 " + refCount + " 条列车路线引用，无法删除");
        }
        stationMapper.deleteById(id);
    }
}
