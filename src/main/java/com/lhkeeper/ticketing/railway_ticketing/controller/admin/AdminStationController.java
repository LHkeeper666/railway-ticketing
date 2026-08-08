package com.lhkeeper.ticketing.railway_ticketing.controller.admin;

import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Station;
import com.lhkeeper.ticketing.railway_ticketing.service.admin.AdminStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/station")
@RequiredArgsConstructor
public class AdminStationController {

    private final AdminStationService adminStationService;

    @GetMapping("/page")
    public Result<PageResponse<Station>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminStationService.page(current, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<Station> getById(@PathVariable Long id) {
        return Result.success(adminStationService.getById(id));
    }

    @PostMapping
    public Result<Station> create(@RequestBody Station station) {
        return Result.success(adminStationService.create(station));
    }

    @PutMapping("/{id}")
    public Result<Station> update(@PathVariable Long id, @RequestBody Station station) {
        return Result.success(adminStationService.update(id, station));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminStationService.delete(id);
        return Result.success();
    }
}
