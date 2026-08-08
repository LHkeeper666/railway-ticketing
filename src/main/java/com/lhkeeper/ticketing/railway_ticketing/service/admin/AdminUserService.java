package com.lhkeeper.ticketing.railway_ticketing.service.admin;

import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.User;

public interface AdminUserService {

    PageResponse<User> page(long current, long size, String keyword, Integer role);

    User getById(Long id);

    void updateStatus(Long id, Integer status);
}
