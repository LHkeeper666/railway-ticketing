package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.OrderCreatePassengerDetailDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Passenger;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Train;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.SeatTypeEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PassengerMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 校验参数是否有效
 * TODO: 考虑缓存？
 */
@Component
@RequiredArgsConstructor
public class OrderCreateParamVerifyChainHandler implements OrderCreateChainFilter<OrderCreateReqDTO> {

    private final TrainMapper trainMapper;
    private final PassengerMapper passengerMapper;

    @Override
    public void handler(OrderCreateReqDTO requestParam) {
        Train train = trainMapper.selectById(requestParam.getTrainId());
        if (train == null) {
            throw new ClientException("当前车次不存在");
        }
        List<String> passengerIds = requestParam.getPassengers().stream()
                .map(OrderCreatePassengerDetailDTO::getPassengerId).toList();
        List<Passenger> passengers = passengerMapper.selectByIds(passengerIds);
        if (passengers.isEmpty() || passengerIds.size() != passengers.size()) {
            throw new ClientException("部分乘车人不存在");
        }
        Long currentUserId = UserContext.get().getUserId();
        for (Passenger passenger : passengers) {
            if (!currentUserId.equals(passenger.getUserId())) {
                throw new ClientException("部分乘车人无效");
            }
        }
        requestParam.getPassengers().forEach(passenger -> {
            if (!SeatTypeEnum.isValidCode(passenger.getSeatType())) {
                throw new ClientException("座位类型不存在");
            }
        });
    }

    @Override
    public int getOrder() {
        return 5;
    }
}
