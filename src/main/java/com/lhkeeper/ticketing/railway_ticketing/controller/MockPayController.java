package com.lhkeeper.ticketing.railway_ticketing.controller;

import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.MockPayPageDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Pay;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.PayStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.service.PaymentService;
import com.lhkeeper.ticketing.railway_ticketing.service.impl.PaymentServiceImpl;
import com.lhkeeper.ticketing.railway_ticketing.service.impl.payment.MockPaymentStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 模拟支付控制器，提供模拟支付页面和确认支付端点。
 */
@Slf4j
@RestController
@RequestMapping("/mock-pay")
@RequiredArgsConstructor
public class MockPayController {

    private final PaymentServiceImpl paymentServiceImpl;
    private final MockPaymentStrategy mockPaymentStrategy;
    private final PaymentService paymentService;

    /**
     * 查看模拟支付页面（订单信息 + 支付确认）
     */
    @GetMapping("/{paySn}")
    public Result<MockPayPageDTO> mockPayPage(@PathVariable("paySn") String paySn) {
        log.info("查看模拟支付页面, paySn={}", paySn);
        Pay pay = paymentServiceImpl.getPayByPaySn(paySn);
        if (pay == null) {
            throw new ClientException("支付记录不存在");
        }

        MockPayPageDTO pageDTO = MockPayPageDTO.builder()
                .paySn(pay.getPaySn())
                .orderSn(pay.getOrderSn())
                .totalAmount(pay.getTotalAmount())
                .status(pay.getStatus())
                .subject(pay.getSubject())
                .build();

        return Result.success(pageDTO);
    }

    /**
     * 确认支付（模拟用户点击支付按钮，走完整回调流程）
     */
    @PostMapping("/{paySn}/pay")
    public Result<Void> mockPayConfirm(@PathVariable("paySn") String paySn) {
        log.info("模拟支付确认, paySn={}", paySn);

        Pay pay = paymentServiceImpl.getPayByPaySn(paySn);
        if (pay == null) {
            throw new ClientException("支付记录不存在");
        }

        // 构造回调 DTO，走完整回调链路（含签名校验）
        String payStatus = PayStatusEnum.SUCCESS.getCode();
        String sign = mockPaymentStrategy.generateSign(
                paySn, pay.getOrderSn(), pay.getTotalAmount(), payStatus);

        PayCallbackReqDTO reqDTO = new PayCallbackReqDTO();
        reqDTO.setOrderSn(pay.getOrderSn());
        reqDTO.setTradeNo(paySn);
        reqDTO.setChannel(MockPaymentStrategy.CHANNEL);
        reqDTO.setTotalAmount(pay.getTotalAmount());
        reqDTO.setStatus(payStatus);
        reqDTO.setGmtPayment(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        reqDTO.setSign(sign);

        paymentService.handleCallback(reqDTO);
        log.info("模拟支付成功, paySn={}, orderSn={}", paySn, pay.getOrderSn());
        return Result.success();
    }
}
