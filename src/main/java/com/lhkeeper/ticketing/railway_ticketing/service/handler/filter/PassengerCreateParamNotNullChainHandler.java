package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import org.springframework.stereotype.Component;

@Component
public class PassengerCreateParamNotNullChainHandler implements PassengerCreateChainFilter<PassengerCreateReqDTO> {

    @Override
    public void handler(PassengerCreateReqDTO requestParam) {
        if (requestParam == null) {
            throw new ClientException("参数不能为空");
        }
        if (StringUtil.isBlank(requestParam.getRealName())) {
            throw new ClientException("真实姓名不能为空");
        }
        if (requestParam.getIdType() == null) {
            throw new ClientException("证件类型不能为空");
        }
        if (StringUtil.isBlank(requestParam.getIdCard())) {
            throw new ClientException("证件号码不能为空");
        }
        if (StringUtil.isBlank(requestParam.getPhone())) {
            throw new ClientException("手机号不能为空");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
