package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.TicketPageQueryReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Region;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.RegionMapper;
import com.lhkeeper.ticketing.railway_ticketing.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class TicketQueryParamVerifyChainHandler implements TicketQueryChainFilter<TicketPageQueryReqDTO> {

    private final RegionMapper regionMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void handler(TicketPageQueryReqDTO requestParam) {

        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.REGION_LOADED_FLAG))) {
            String lockKey = RedisConstant.LOCK_KEY_PREFIX + "region";
            boolean lockAcquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, "locked", RedisConstant.LOCK_TTL_SECONDS, TimeUnit.SECONDS));
            if (!lockAcquired) {
                throw new ServiceException("系统正忙，请稍后重试");
            }
            // 双重检查
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisConstant.REGION_LOADED_FLAG))) {
                try {
                    List<Region> regions = regionMapper.selectList(Wrappers.emptyWrapper());
                    for (Region region : regions) {
                        String key = String.format(RedisConstant.REGION_CODE_TO_REGION_NAME_MAPPING, region.getCode());
                        stringRedisTemplate.opsForValue().set(key, region.getName(),
                                RedisConstant.CACHE_TTL_REGION + ThreadLocalRandom.current().nextLong(RedisConstant.CACHE_TTL_REGION / 10),
                                TimeUnit.SECONDS);
                    }
                    stringRedisTemplate.opsForValue().set(RedisConstant.REGION_LOADED_FLAG, "1");
                } finally {
                    stringRedisTemplate.delete(lockKey);
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
