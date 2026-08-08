package com.lhkeeper.ticketing.railway_ticketing.service.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Region;

public interface AdminRegionService {

    PageResponse<Region> page(long current, long size, String keyword);

    Region getById(Long id);

    Region create(Region region);

    Region update(Long id, Region region);

    void delete(Long id);
}
