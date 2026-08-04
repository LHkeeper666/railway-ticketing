package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RefundReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Order;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Passenger;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Ticket;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.OrderMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PassengerMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 退票订单状态及权限校验（order 1）。
 * 参考 12306 规则：下单人可退任意票，乘车人可退本人的票。
 */
@Component
@RequiredArgsConstructor
public class OrderRefundStatusChainHandler implements OrderRefundChainFilter {

    private final OrderMapper orderMapper;
    private final TicketMapper ticketMapper;
    private final PassengerMapper passengerMapper;

    @Override
    public void handler(RefundReqDTO reqDTO) {
        Order order = orderMapper.selectOne(
                Wrappers.lambdaQuery(Order.class)
                        .eq(Order::getOrderSn, reqDTO.getOrderSn())
        );
        if (order == null) {
            throw new ClientException("订单不存在");
        }
        if (!OrderStatusEnum.PAID.getCode().equals(order.getStatus())) {
            throw new ClientException("只有已支付订单才能退票");
        }

        // 下单人：可退任意票
        if (order.getUserId().equals(UserContext.get().getUserId())) {
            return;
        }

        // 非下单人：校验是否为本人的票（12306 乘车人独立退票场景）
        List<Passenger> userPassengers = passengerMapper.selectList(
                Wrappers.lambdaQuery(Passenger.class)
                        .eq(Passenger::getUsername, UserContext.get().getUsername())
        );
        if (userPassengers.isEmpty()) {
            throw new ClientException("无权操作此订单");
        }
        Set<Long> userPassengerIds = userPassengers.stream()
                .map(Passenger::getId)
                .collect(Collectors.toSet());

        List<Ticket> tickets = ticketMapper.selectBatchIds(reqDTO.getTicketIds());
        if (tickets.size() != reqDTO.getTicketIds().size()) {
            throw new ClientException("部分车票不存在");
        }
        for (Ticket ticket : tickets) {
            if (!ticket.getOrderSn().equals(reqDTO.getOrderSn())) {
                throw new ClientException("车票不属于此订单");
            }
            if (!userPassengerIds.contains(ticket.getPassengerId())) {
                throw new ClientException("无权退非本人车票");
            }
        }
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
