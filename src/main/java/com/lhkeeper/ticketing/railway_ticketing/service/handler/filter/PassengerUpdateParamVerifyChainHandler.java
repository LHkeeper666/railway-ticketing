package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerUpdateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Passenger;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.DiscountTypeEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PassengerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PassengerUpdateParamVerifyChainHandler implements PassengerUpdateChainFilter<PassengerUpdateReqDTO> {

    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";

    private final PassengerMapper passengerMapper;

    @Override
    public void handler(PassengerUpdateReqDTO requestParam) {
        // PassengerId is passed through a different mechanism — the service layer
        // sets it before calling the chain. The chain validates the body fields.
        if (requestParam.getPhone() != null && !requestParam.getPhone().matches(PHONE_PATTERN)) {
            throw new ClientException("手机号格式不正确");
        }
        if (requestParam.getDiscountType() != null
                && !DiscountTypeEnum.isValidCode(requestParam.getDiscountType())) {
            throw new ClientException("优惠类型无效");
        }
    }

    @Override
    public int getOrder() {
        return 5;
    }
}
