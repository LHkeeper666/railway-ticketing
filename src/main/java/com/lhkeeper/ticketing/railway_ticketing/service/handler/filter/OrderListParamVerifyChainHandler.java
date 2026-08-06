package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderListReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class OrderListParamVerifyChainHandler implements OrderListChainFilter<OrderListReqDTO> {

    @Override
    public void handler(OrderListReqDTO requestParam) {
        if (requestParam == null) {
            throw new ClientException("参数不能为空");
        }

        if (requestParam.getStatus() != null) {
            boolean valid = Arrays.stream(OrderStatusEnum.values())
                    .anyMatch(e -> e.getCode().equals(requestParam.getStatus()));
            if (!valid) {
                throw new ClientException("订单状态值无效");
            }
        }

        if (requestParam.getSize() != null && requestParam.getSize() > 50) {
            throw new ClientException("每页最多查询50条");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
