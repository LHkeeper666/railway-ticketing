package com.lhkeeper.ticketing.railway_ticketing.controller.admin;

import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderDetailRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.OrderListRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.service.admin.AdminOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @GetMapping("/page")
    public Result<PageResponse<OrderListRespDTO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String trainNumber,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return Result.success(adminOrderService.page(current, size, status, trainNumber, userId, startDate, endDate));
    }

    @GetMapping("/{orderSn}")
    public Result<OrderDetailRespDTO> getDetail(@PathVariable String orderSn) {
        return Result.success(adminOrderService.getDetail(orderSn));
    }

    @PostMapping("/{orderSn}/cancel")
    public Result<Void> cancel(@PathVariable String orderSn) {
        adminOrderService.cancel(orderSn);
        return Result.success();
    }

    @PostMapping("/{orderSn}/refund")
    public Result<Void> refund(@PathVariable String orderSn,
                                @RequestParam(required = false, defaultValue = "管理员操作") String reason) {
        adminOrderService.refund(orderSn, reason);
        return Result.success();
    }
}
