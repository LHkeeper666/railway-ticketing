package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Region;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.RegionMapper;
import com.lhkeeper.ticketing.railway_ticketing.util.DateUtil;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 余票查询参数有效性校验：Region 存在性、日期合法性（order 5）
 */
@Component
@RequiredArgsConstructor
public class TicketQueryParamVerifyChainHandler implements TicketQueryChainFilter<TicketPageQueryReqDTO> {

    private final RegionMapper regionMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final DistributedLockFactory lockFactory;

    @Override
    public void handler(TicketPageQueryReqDTO requestParam) {

        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.REGION_LOADED_FLAG))) {
            DistributedLock lock = lockFactory.tryLock("region", RedisConstant.LOCK_TTL_SECONDS);
            // 自旋重试，等待持锁者加载完成（Redis 重启后避免大面积 500）
            for (int retry = 0; lock == null && retry < 10; retry++) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ServiceException("系统繁忙，请稍后重试");
                }
                if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.REGION_LOADED_FLAG))) {
                    break;
                }
                lock = lockFactory.tryLock("region", RedisConstant.LOCK_TTL_SECONDS);
            }
            if (lock == null && !Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.REGION_LOADED_FLAG))) {
                throw new ServiceException("系统正忙，请稍后重试");
            }
            if (lock != null) {
                try {
                    // 双重检查
                    if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.REGION_LOADED_FLAG))) {
                        List<Region> regions = regionMapper.selectList(Wrappers.emptyWrapper());
                        for (Region region : regions) {
                            String key = String.format(RedisConstant.REGION_CODE_TO_REGION_NAME_MAPPING, region.getCode());
                            long ttl = RedisConstant.CACHE_TTL_REGION
                                    + ThreadLocalRandom.current().nextLong(RedisConstant.CACHE_TTL_REGION / 10);
                            stringRedisTemplate.opsForValue().set(key, region.getName(), ttl, TimeUnit.SECONDS);
                        }
                        stringRedisTemplate.opsForValue().set(RedisConstant.REGION_LOADED_FLAG, "1",
                                RedisConstant.CACHE_TTL_REGION, TimeUnit.SECONDS);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
        String startRegionName = stringRedisTemplate.opsForValue().get(
                String.format(RedisConstant.REGION_CODE_TO_REGION_NAME_MAPPING, requestParam.getStartRegionCode()));
        String endRegionName = stringRedisTemplate.opsForValue().get(
                String.format(RedisConstant.REGION_CODE_TO_REGION_NAME_MAPPING, requestParam.getEndRegionCode()));

        if (startRegionName == null) {
            throw new ClientException("出发地不存在");
        }
        if (endRegionName == null) {
            throw new ClientException("目的地不存在");
        }
        if (Objects.equals(requestParam.getStartRegionCode(), requestParam.getEndRegionCode())) {
            throw new ClientException("出发地和目的地不能相同");
        }
        // 校验日期
//        if (!DateUtil.validateFormat(requestParam.getDepartureDate(), "yyyy-MM-dd")) {
//            throw new ClientException("出发日期格式错误，必须使用格式\"yyyy-MM-dd\"");
//        }
        if (DateUtil.beforeToday(requestParam.getDepartureDate())) {
            throw new ClientException("出发日期不能小于当前日期");
        }
    }

    @Override
    public int getOrder() {
        return 5;
    }
}
