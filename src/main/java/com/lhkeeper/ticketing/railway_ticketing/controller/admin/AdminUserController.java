package com.lhkeeper.ticketing.railway_ticketing.controller.admin;

import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.User;
import com.lhkeeper.ticketing.railway_ticketing.service.admin.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping("/page")
    public Result<PageResponse<User>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer role) {
        return Result.success(adminUserService.page(current, size, keyword, role));
    }

    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable Long id) {
        return Result.success(adminUserService.getById(id));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        adminUserService.updateStatus(id, body.get("status"));
        return Result.success();
    }
}
