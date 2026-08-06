package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.service.PaymentStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支付回调签名校验（order=3，在参数非空之后、业务校验之前）
 */
@Component
@RequiredArgsConstructor
public class PayNotifySignVerifyChainHandler implements PayNotifyChainFilter<PayCallbackReqDTO> {

    private final List<PaymentStrategy> strategies;

    private Map<String, PaymentStrategy> strategyMap;

    private Map<String, PaymentStrategy> getStrategyMap() {
        if (strategyMap == null) {
            strategyMap = strategies.stream()
                    .collect(Collectors.toMap(PaymentStrategy::getChannel, Function.identity()));
        }
        return strategyMap;
    }

    @Override
    public void handler(PayCallbackReqDTO requestParam) {
        String channel = requestParam.getChannel();
        PaymentStrategy strategy = getStrategyMap().get(channel);
        if (strategy == null) {
            throw new ClientException("不支持的支付渠道: " + channel);
        }
        if (!strategy.verifySignature(requestParam)) {
            throw new ClientException("签名校验失败");
        }
    }

    @Override
    public int getOrder() {
        return 3;
    }
}
