package com.lhkeeper.ticketing.railway_ticketing.controller;

import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PayCallbackReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderCreateRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderDetailRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public Result<OrderCreateRespDTO> createOrder(@RequestBody OrderCreateReqDTO reqDTO) {
        return Result.success(orderService.createOrder(reqDTO));
    }

    @PostMapping("/{orderSn}/pay")
    public Result<?> payOrder(@PathVariable("orderSn") String orderSn) {
        try {
            orderService.payOrder(orderSn);
            return Result.success();
        } catch (ClientException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/pay/notify")
    public Result<?> payNotify(@RequestBody PayCallbackReqDTO reqDTO) {
        try {
            orderService.handlePayNotify(reqDTO);
            return Result.success();
        } catch (ClientException e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/{orderSn}")
    public Result<OrderDetailRespDTO> getOrderDetail(@PathVariable("orderSn") String orderSn) {
        return Result.success(orderService.getOrderDetail(orderSn));
    }

    @PostMapping("/{orderSn}/cancel")
    public Result<?> cancelOrder(@PathVariable("orderSn") String orderSn) {
        try {
            orderService.cancelOrder(orderSn);
            return Result.success();
        } catch (ClientException e) {
            return Result.fail(e.getMessage());
        }
    }
}
