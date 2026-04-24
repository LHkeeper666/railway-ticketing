package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import org.springframework.stereotype.Component;

@Component
public class OrderCreateParamNotNullChainHandler implements OrderCreateChainFilter<OrderCreateReqDTO> {

    @Override
    public void handler(OrderCreateReqDTO requestParam) {
        System.out.println("开始handler了");
        if (requestParam == null) {
            throw new ClientException("参数不能为空");
        }
        if (requestParam.getTrainId() == null) {
            throw new ClientException("列车标识不能为空");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
