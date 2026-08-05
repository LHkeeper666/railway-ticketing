package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerUpdateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import org.springframework.stereotype.Component;

@Component
public class PassengerUpdateParamNotNullChainHandler implements PassengerUpdateChainFilter<PassengerUpdateReqDTO> {

    @Override
    public void handler(PassengerUpdateReqDTO requestParam) {
        if (requestParam == null) {
            throw new ClientException("参数不能为空");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
