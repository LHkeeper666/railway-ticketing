package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Passenger;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Ticket;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.TicketStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PassengerMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TicketMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PassengerDeleteParamVerifyChainHandler implements PassengerDeleteChainFilter {

    private final PassengerMapper passengerMapper;
    private final TicketMapper ticketMapper;

    @Override
    public void handler(Long passengerId) {
        if (passengerId == null) {
            throw new ClientException("乘车人ID不能为空");
        }
        Passenger passenger = passengerMapper.selectById(passengerId);
        if (passenger == null) {
            throw new ClientException("乘车人不存在");
        }
        Long currentUserId = UserContext.get().getUserId();
        if (!currentUserId.equals(passenger.getUserId())) {
            throw new ClientException("乘车人不存在或无权操作");
        }

        boolean hasActiveTickets = ticketMapper.exists(
                Wrappers.lambdaQuery(Ticket.class)
                        .eq(Ticket::getPassengerId, passengerId)
                        .in(Ticket::getTicketStatus,
                                TicketStatusEnum.UNPAID.getCode(),
                                TicketStatusEnum.PAID.getCode())
        );
        if (hasActiveTickets) {
            throw new ClientException("该乘车人有未完成的订单，无法删除");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
