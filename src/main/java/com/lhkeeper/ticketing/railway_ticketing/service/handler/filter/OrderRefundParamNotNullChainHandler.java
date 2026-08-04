package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RefundReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import org.springframework.stereotype.Component;

/**
 * 退票参数非空校验（order 0）
 */
@Component
public class OrderRefundParamNotNullChainHandler implements OrderRefundChainFilter {

    @Override
    public void handler(RefundReqDTO reqDTO) {
        if (StringUtil.isBlank(reqDTO.getOrderSn())) {
            throw new ClientException("订单号不能为空");
        }
        if (reqDTO.getTicketIds() == null || reqDTO.getTicketIds().isEmpty()) {
            throw new ClientException("请选择要退票的车票");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
