package com.lhkeeper.ticketing.railway_ticketing.controller.admin;

import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Region;
import com.lhkeeper.ticketing.railway_ticketing.service.admin.AdminRegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/region")
@RequiredArgsConstructor
public class AdminRegionController {

    private final AdminRegionService adminRegionService;

    @GetMapping("/page")
    public Result<PageResponse<Region>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminRegionService.page(current, size, keyword));
    }

    @GetMapping("/{id}")
    public Result<Region> getById(@PathVariable Long id) {
        return Result.success(adminRegionService.getById(id));
    }

    @PostMapping
    public Result<Region> create(@RequestBody Region region) {
        return Result.success(adminRegionService.create(region));
    }

    @PutMapping("/{id}")
    public Result<Region> update(@PathVariable Long id, @RequestBody Region region) {
        return Result.success(adminRegionService.update(id, region));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminRegionService.delete(id);
        return Result.success();
    }
}
