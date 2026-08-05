package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Passenger;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.DiscountTypeEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.IdTypeEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PassengerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PassengerCreateParamVerifyChainHandler implements PassengerCreateChainFilter<PassengerCreateReqDTO> {

    private static final int MAX_PASSENGERS_PER_USER = 15;
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";

    private final PassengerMapper passengerMapper;

    @Override
    public void handler(PassengerCreateReqDTO requestParam) {
        if (!IdTypeEnum.isValidCode(requestParam.getIdType())) {
            throw new ClientException("证件类型无效");
        }
        if (requestParam.getDiscountType() != null
                && !DiscountTypeEnum.isValidCode(requestParam.getDiscountType())) {
            throw new ClientException("优惠类型无效");
        }
        if (requestParam.getPhone() != null && !requestParam.getPhone().matches(PHONE_PATTERN)) {
            throw new ClientException("手机号格式不正确");
        }

        Long userId = UserContext.get().getUserId();
        boolean exists = passengerMapper.exists(
                Wrappers.lambdaQuery(Passenger.class)
                        .eq(Passenger::getUserId, userId)
                        .eq(Passenger::getIdCard, requestParam.getIdCard())
        );
        if (exists) {
            throw new ClientException("乘车人已存在");
        }

        long count = passengerMapper.selectCount(
                Wrappers.lambdaQuery(Passenger.class)
                        .eq(Passenger::getUserId, userId)
        );
        if (count >= MAX_PASSENGERS_PER_USER) {
            throw new ClientException("每位用户最多添加15位乘车人");
        }
    }

    @Override
    public int getOrder() {
        return 5;
    }
}
