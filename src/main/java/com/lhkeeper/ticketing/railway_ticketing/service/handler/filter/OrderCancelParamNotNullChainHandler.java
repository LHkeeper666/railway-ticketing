package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import org.springframework.stereotype.Component;

/**
 * 订单取消参数非空校验（order 0）
 */
@Component
public class OrderCancelParamNotNullChainHandler implements OrderCancelChainFilter<String> {

    @Override
    public void handler(String orderSn) {
        if (StringUtil.isBlank(orderSn)) {
            throw new ClientException("订单号不能为空");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
