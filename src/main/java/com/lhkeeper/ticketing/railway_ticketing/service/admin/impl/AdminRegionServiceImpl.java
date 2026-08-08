package com.lhkeeper.ticketing.railway_ticketing.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Region;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Station;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Train;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.RegionMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.StationMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.admin.AdminRegionService;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminRegionServiceImpl implements AdminRegionService {

    private final RegionMapper regionMapper;
    private final StationMapper stationMapper;
    private final TrainMapper trainMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public PageResponse<Region> page(long current, long size, String keyword) {
        LambdaQueryWrapper<Region> wrapper = new LambdaQueryWrapper<Region>()
                .orderByAsc(Region::getSpell);
        if (!StringUtil.isBlank(keyword)) {
            wrapper.and(w -> w.like(Region::getName, keyword)
                    .or().like(Region::getFullName, keyword)
                    .or().like(Region::getCode, keyword));
        }
        Page<Region> page = new Page<>(current, size);
        IPage<Region> result = regionMapper.selectPage(page, wrapper);
        return PageResponse.from(result, result.getRecords());
    }

    @Override
    public Region getById(Long id) {
        Region region = regionMapper.selectById(id);
        if (region == null) {
            throw new ClientException("区域不存在");
        }
        return region;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Region create(Region region) {
        regionMapper.insert(region);
        clearRegionCache();
        return region;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Region update(Long id, Region region) {
        Region existing = getById(id);
        region.setId(id);
        regionMapper.updateById(region);
        clearRegionCache();
        return region;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void delete(Long id) {
        Region region = getById(id);
        // check station references
        long stationCount = stationMapper.selectCount(
                new LambdaQueryWrapper<Station>().eq(Station::getRegionCode, region.getCode())
        );
        if (stationCount > 0) {
            throw new ClientException("该区域下有 " + stationCount + " 个站点，无法删除");
        }
        // check train references
        long trainCount = trainMapper.selectCount(
                new LambdaQueryWrapper<Train>()
                        .eq(Train::getStartRegion, region.getName())
                        .or().eq(Train::getEndRegion, region.getName())
        );
        if (trainCount > 0) {
            throw new ClientException("该区域被 " + trainCount + " 个车次引用，无法删除");
        }
        regionMapper.deleteById(id);
        clearRegionCache();
    }

    private void clearRegionCache() {
        Set<String> keys = stringRedisTemplate.keys(
                String.format(RedisConstant.REGION_CODE_TO_REGION_NAME_MAPPING, "*")
        );
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
        stringRedisTemplate.delete(RedisConstant.REGION_LOADED_FLAG);
    }
}
