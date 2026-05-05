package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 订单创建参数非空校验（order 0）
 */
@Component
public class OrderCreateParamNotNullChainHandler implements OrderCreateChainFilter<OrderCreateReqDTO> {

    @Override
    public void handler(OrderCreateReqDTO requestParam) {
        if (requestParam == null) {
            throw new ClientException("参数不能为空");
        }
        if (StringUtil.isBlank(requestParam.getTrainId())) {
            throw new ClientException("列车标识不能为空");
        }
        if (StringUtil.isBlank(requestParam.getStartStation())) {
            throw new ClientException("出发站点不能为空");
        }
        if (StringUtil.isBlank(requestParam.getEndStation())) {
            throw new ClientException("目的站点不能为空");
        }
        if (requestParam.getPassengers() == null || requestParam.getPassengers().isEmpty()) {
            throw new ClientException("乘车人至少选择一位");
        }
        requestParam.getPassengers().forEach(p -> {
            if (StringUtil.isBlank(p.getPassengerId())) {
                throw new ClientException("乘车人不能为空");
            }
            if (Objects.isNull(p.getSeatType())) {
                throw new ClientException("座位类型不能为空");
            }
        });
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
