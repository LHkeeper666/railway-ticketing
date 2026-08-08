package com.lhkeeper.ticketing.railway_ticketing.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.User;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.UserMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.admin.AdminUserService;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;

    @Override
    public PageResponse<User> page(long current, long size, String keyword, Integer role) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getCreateTime);
        if (!StringUtil.isBlank(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getRealName, keyword)
                    .or().like(User::getPhone, keyword));
        }
        if (role != null) {
            wrapper.eq(User::getRole, role);
        }
        Page<User> page = new Page<>(current, size);
        IPage<User> result = userMapper.selectPage(page, wrapper);
        // 脱敏
        for (User user : result.getRecords()) {
            user.setPassword(null);
            if (user.getIdCard() != null && user.getIdCard().length() > 7) {
                user.setIdCard(user.getIdCard().substring(0, 3) + "****" + user.getIdCard().substring(user.getIdCard().length() - 4));
            }
        }
        return PageResponse.from(result, result.getRecords());
    }

    @Override
    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new ClientException("用户不存在");
        user.setPassword(null);
        if (user.getIdCard() != null && user.getIdCard().length() > 7) {
            user.setIdCard(user.getIdCard().substring(0, 3) + "****" + user.getIdCard().substring(user.getIdCard().length() - 4));
        }
        return user;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void updateStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) throw new ClientException("用户不存在");
        user.setStatus(status);
        userMapper.updateById(user);
    }
}
