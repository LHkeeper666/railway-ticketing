package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.OrderItem;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderItemMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItem> implements OrderItemService {


}
