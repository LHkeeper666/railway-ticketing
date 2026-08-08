package com.lhkeeper.ticketing.railway_ticketing.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RefundReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderDetailRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderListRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.OrderItem;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderItemMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import com.lhkeeper.ticketing.railway_ticketing.service.RefundService;
import com.lhkeeper.ticketing.railway_ticketing.service.admin.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderService orderService;
    private final RefundService refundService;

    @Override
    public PageResponse<OrderListRespDTO> page(long current, long size, Integer status,
                                                String trainNumber, Long userId,
                                                LocalDate startDate, LocalDate endDate) {
        Page<Order> page = new Page<>(current, size);
        IPage<Order> orderPage = orderMapper.selectPage(page,
                Wrappers.<Order>lambdaQuery()
                        .eq(status != null, Order::getStatus, status)
                        .eq(userId != null, Order::getUserId, userId)
                        .like(trainNumber != null, Order::getTrainNumber, trainNumber)
                        .ge(startDate != null, Order::getRidingDate, startDate)
                        .le(endDate != null, Order::getRidingDate, endDate)
                        .orderByDesc(Order::getOrderTime));

        List<String> orderSns = orderPage.getRecords().stream()
                .map(Order::getOrderSn).collect(Collectors.toList());

        Map<String, List<OrderItem>> itemMap = Collections.emptyMap();
        if (!orderSns.isEmpty()) {
            itemMap = orderItemMapper.selectList(
                    Wrappers.<OrderItem>lambdaQuery().in(OrderItem::getOrderSn, orderSns))
                    .stream().collect(Collectors.groupingBy(OrderItem::getOrderSn));
        }
        final Map<String, List<OrderItem>> finalItemMap = itemMap;

        List<OrderListRespDTO> records = orderPage.getRecords().stream()
                .map(order -> {
                    List<OrderItem> items = finalItemMap.getOrDefault(order.getOrderSn(), Collections.emptyList());
                    BigDecimal totalAmount = items.stream()
                            .map(OrderItem::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return OrderListRespDTO.builder()
                            .orderSn(order.getOrderSn())
                            .trainNumber(order.getTrainNumber())
                            .ridingDate(order.getRidingDate())
                            .startStation(order.getStartStation())
                            .endStation(order.getEndStation())
                            .departureTime(order.getDepartureTime())
                            .arrivalTime(order.getArrivalTime())
                            .status(order.getStatus())
                            .orderTime(order.getOrderTime())
                            .totalAmount(totalAmount)
                            .passengerCount(items.size())
                            .build();
                }).collect(Collectors.toList());

        return PageResponse.from(orderPage, records);
    }

    @Override
    public OrderDetailRespDTO getDetail(String orderSn) {
        return orderService.getOrderDetail(orderSn);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void cancel(String orderSn) {
        orderService.cancelOrder(orderSn, false);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void refund(String orderSn, String reason) {
        List<OrderItem> items = orderItemMapper.selectList(
                Wrappers.<OrderItem>lambdaQuery().eq(OrderItem::getOrderSn, orderSn)
        );
        List<Long> ticketIds = items.stream()
                .map(OrderItem::getId)
                .collect(Collectors.toList());

        RefundReqDTO reqDTO = new RefundReqDTO();
        reqDTO.setOrderSn(orderSn);
        reqDTO.setTicketIds(ticketIds);
        reqDTO.setReason(reason);
        refundService.refund(reqDTO);
    }
}
