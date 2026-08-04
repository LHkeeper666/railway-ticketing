package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.ChangeReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import org.springframework.stereotype.Component;

/**
 * 改签参数非空校验（order 0）
 */
@Component
public class OrderChangeParamNotNullChainHandler implements OrderChangeChainFilter {

    @Override
    public void handler(ChangeReqDTO reqDTO) {
        if (StringUtil.isBlank(reqDTO.getOrderSn())) {
            throw new ClientException("订单号不能为空");
        }
        if (reqDTO.getTicketIds() == null || reqDTO.getTicketIds().isEmpty()) {
            throw new ClientException("请选择要改签的车票");
        }
        if (StringUtil.isBlank(reqDTO.getNewTrainId())) {
            throw new ClientException("请选择新车次");
        }
        if (StringUtil.isBlank(reqDTO.getNewStartStation())) {
            throw new ClientException("请选择新出发站");
        }
        if (StringUtil.isBlank(reqDTO.getNewEndStation())) {
            throw new ClientException("请选择新到达站");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
