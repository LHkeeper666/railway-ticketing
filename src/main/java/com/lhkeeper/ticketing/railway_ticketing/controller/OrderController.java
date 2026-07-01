package com.lhkeeper.ticketing.railway_ticketing.controller;

import com.lhkeeper.ticketing.railway_ticketing.common.annotation.RateLimit;
import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.WaitlistCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.FlashOrderCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderDetailRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.WaitlistCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.WaitlistDetailRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import com.lhkeeper.ticketing.railway_ticketing.service.WaitlistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 订单控制器，处理订单创建、支付、取消、详情查询等请求
 */
@Slf4j
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private WaitlistService waitlistService;

    /**
     * 创建订单（普通购票）
     */
    @RateLimit(key = "order:create", capacity = 20, refillRate = 10.0)
    @PostMapping("/create")
    public Result<OrderCreateRespDTO> createOrder(@RequestBody OrderCreateReqDTO reqDTO) {
        log.info("收到创建订单请求, trainId={}, passengers={}", reqDTO.getTrainId(), reqDTO.getPassengers().size());
        return Result.success(orderService.createOrder(reqDTO));
    }

    /**
     * 抢票排队，订单写入 MQ 后立即返回
     */
    @RateLimit(key = "order:flash-create", capacity = 50, refillRate = 30.0)
    @PostMapping("/flash-create")
    public Result<FlashOrderCreateRespDTO> flashCreate(@RequestBody OrderCreateReqDTO reqDTO) {
        log.info("收到抢票请求, trainId={}, passengers={}", reqDTO.getTrainId(), reqDTO.getPassengers().size());
        return Result.success(orderService.flashCreateOrder(reqDTO));
    }

    /**
     * 模拟支付
     */
    @PostMapping("/{orderSn}/pay")
    public Result<?> payOrder(@PathVariable("orderSn") String orderSn) {
        log.info("收到支付请求, orderSn={}", orderSn);
        try {
            orderService.payOrder(orderSn);
            return Result.success();
        } catch (ClientException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 支付回调，接收第三方支付平台通知（无需 JWT 认证）
     */
    @PostMapping("/pay/notify")
    public Result<?> payNotify(@RequestBody PayCallbackReqDTO reqDTO) {
        log.info("收到支付回调请求, orderSn={}, status={}", reqDTO.getOrderSn(), reqDTO.getStatus());
        try {
            orderService.handlePayNotify(reqDTO);
            return Result.success();
        } catch (ClientException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderSn}")
    public Result<OrderDetailRespDTO> getOrderDetail(@PathVariable("orderSn") String orderSn) {
        return Result.success(orderService.getOrderDetail(orderSn));
    }

    /**
     * 取消订单
     */
    @PostMapping("/{orderSn}/cancel")
    public Result<?> cancelOrder(@PathVariable("orderSn") String orderSn) {
        log.info("收到取消订单请求, orderSn={}", orderSn);
        try {
            orderService.cancelOrder(orderSn);
            return Result.success();
        } catch (ClientException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 提交候补订单
     */
    @RateLimit(key = "order:waitlist-create", capacity = 50, refillRate = 30.0)
    @PostMapping("/waitlist-create")
    public Result<WaitlistCreateRespDTO> createWaitlist(@RequestBody WaitlistCreateReqDTO reqDTO) {
        log.info("收到候补请求, trainId={}, seatType={}, passengers={}", reqDTO.getTrainId(), reqDTO.getSeatType(), reqDTO.getPassengers().size());
        return Result.success(waitlistService.createWaitlist(reqDTO));
    }

    /**
     * 查询候补状态
     */
    @GetMapping("/waitlist/{waitlistSn}")
    public Result<WaitlistDetailRespDTO> getWaitlistDetail(@PathVariable("waitlistSn") String waitlistSn) {
        return Result.success(waitlistService.getWaitlistDetail(waitlistSn));
    }

    /**
     * 取消候补
     */
    @PostMapping("/waitlist/{waitlistSn}/cancel")
    public Result<?> cancelWaitlist(@PathVariable("waitlistSn") String waitlistSn) {
        log.info("收到取消候补请求, waitlistSn={}", waitlistSn);
        try {
            waitlistService.cancelWaitlist(waitlistSn);
            return Result.success();
        } catch (ClientException e) {
            return Result.fail(e.getMessage());
        }
    }
}
