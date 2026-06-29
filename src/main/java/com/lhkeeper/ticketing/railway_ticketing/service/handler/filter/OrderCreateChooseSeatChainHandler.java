package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.SeatNumberParser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单创建选座偏好校验（order 2）
 */
@Component
public class OrderCreateChooseSeatChainHandler implements OrderCreateChainFilter<OrderCreateReqDTO> {

    @Override
    public void handler(OrderCreateReqDTO requestParam) {
        if (requestParam == null || requestParam.getPassengers() == null) {
            return; // 前置校验已处理
        }

        List<String> chooseSeats = requestParam.getChooseSeats();
        if (chooseSeats == null || chooseSeats.isEmpty()) {
            return; // 可选参数，为空则跳过
        }

        int passengerCount = requestParam.getPassengers().size();

        // 校验偏好数量不能超过乘客数量
        if (chooseSeats.size() > passengerCount) {
            throw new ClientException("偏好数量不能超过乘客数量");
        }

        // 校验偏好字符有效性
        for (String seat : chooseSeats) {
            if (seat == null || seat.isEmpty()) {
                throw new ClientException("位置偏好不能为空");
            }
            char position = seat.charAt(0);
            if (!SeatNumberParser.isValidPosition(position)) {
                throw new ClientException("无效的位置偏好: " + position);
            }
        }
    }

    @Override
    public int getOrder() {
        return 2;
    }
}
