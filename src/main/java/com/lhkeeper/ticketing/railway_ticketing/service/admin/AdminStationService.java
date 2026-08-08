package com.lhkeeper.ticketing.railway_ticketing.service.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Station;

public interface AdminStationService {

    PageResponse<Station> page(long current, long size, String keyword);

    Station getById(Long id);

    Station create(Station station);

    Station update(Long id, Station station);

    void delete(Long id);
}
