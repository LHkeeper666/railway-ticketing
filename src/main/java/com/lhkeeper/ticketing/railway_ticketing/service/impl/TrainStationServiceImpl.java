package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStation;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.TrainStationService;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TrainStationServiceImpl
        extends ServiceImpl<TrainStationMapper, TrainStation>
        implements TrainStationService {

    private final StringRedisTemplate stringRedisTemplate;
    private final DistributedLockFactory lockFactory;

    @Override
    public List<TrainStation> getTrainStationsByTrainId(Long trainId) {
        String cacheKey = String.format(RedisConstant.TRAIN_STATION_LIST, trainId);
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cachedJson != null) {
            if (RedisConstant.NULL_PLACEHOLDER.equals(cachedJson)) {
                return Collections.emptyList();
            }
            return JSON.parseArray(cachedJson, TrainStation.class);
        }

        DistributedLock lock = lockFactory.tryLock("train_station_list:" + trainId, RedisConstant.LOCK_TTL_SECONDS);
        if (lock == null) {
            throw new ServiceException("系统正忙，请稍后重试");
        }
        try {
            cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null) {
                if (RedisConstant.NULL_PLACEHOLDER.equals(cachedJson)) {
                    return Collections.emptyList();
                }
                return JSON.parseArray(cachedJson, TrainStation.class);
            }

            List<TrainStation> stations = getBaseMapper().selectList(
                    Wrappers.lambdaQuery(TrainStation.class)
                            .eq(TrainStation::getTrainId, trainId)
            );

            if (stations.isEmpty()) {
                stringRedisTemplate.opsForValue().set(cacheKey, RedisConstant.NULL_PLACEHOLDER,
                        RedisConstant.CACHE_TTL_NULL, TimeUnit.SECONDS);
            } else {
                stringRedisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(stations),
                        RedisConstant.CACHE_TTL_TRAIN_STATION + ThreadLocalRandom.current().nextLong(RedisConstant.CACHE_TTL_TRAIN_STATION / 10),
                        TimeUnit.SECONDS);
            }
            return stations;
        } finally {
            lock.unlock();
        }
    }
}
