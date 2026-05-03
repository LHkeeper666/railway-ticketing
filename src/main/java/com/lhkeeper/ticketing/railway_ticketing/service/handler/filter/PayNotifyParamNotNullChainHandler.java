package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import org.springframework.stereotype.Component;

@Component
public class PayNotifyParamNotNullChainHandler implements PayNotifyChainFilter<PayCallbackReqDTO> {

    @Override
    public void handler(PayCallbackReqDTO requestParam) {
        if (requestParam == null) {
            throw new ClientException("参数不能为空");
        }
        if (StringUtil.isBlank(requestParam.getOrderSn())) {
            throw new ClientException("订单号不能为空");
        }
        if (StringUtil.isBlank(requestParam.getTradeNo())) {
            throw new ClientException("交易流水号不能为空");
        }
        if (StringUtil.isBlank(requestParam.getChannel())) {
            throw new ClientException("支付渠道不能为空");
        }
        if (StringUtil.isBlank(requestParam.getStatus())) {
            throw new ClientException("支付状态不能为空");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
