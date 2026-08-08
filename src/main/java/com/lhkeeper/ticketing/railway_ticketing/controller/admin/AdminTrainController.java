package com.lhkeeper.ticketing.railway_ticketing.controller.admin;

import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.service.admin.AdminTrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/train")
@RequiredArgsConstructor
public class AdminTrainController {

    private final AdminTrainService adminTrainService;

    // ==================== 列车基本信息 ====================

    @GetMapping("/page")
    public Result<PageResponse<Train>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) String trainNumber,
            @RequestParam(required = false) Integer trainType) {
        return Result.success(adminTrainService.page(current, size, trainNumber, trainType));
    }

    @GetMapping("/{id}")
    public Result<Train> getDetail(@PathVariable Long id) {
        return Result.success(adminTrainService.getDetail(id));
    }

    @GetMapping("/{id}/stations")
    public Result<List<TrainStation>> getStations(@PathVariable Long id) {
        return Result.success(adminTrainService.getStations(id));
    }

    @PostMapping
    public Result<Train> create(@RequestBody Train train) {
        return Result.success(adminTrainService.create(train));
    }

    @PutMapping("/{id}")
    public Result<Train> updateMeta(@PathVariable Long id, @RequestBody Train train) {
        return Result.success(adminTrainService.updateMeta(id, train));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminTrainService.delete(id);
        return Result.success();
    }

    // ==================== 路线管理 ====================

    @PutMapping("/{id}/stations")
    public Result<Void> setStations(@PathVariable Long id,
                                    @RequestBody List<TrainStationReqDTO> stations) {
        adminTrainService.setStations(id, stations);
        return Result.success();
    }

    @PostMapping("/{id}/stations/append")
    public Result<Void> appendStation(@PathVariable Long id,
                                      @RequestBody TrainStationReqDTO dto) {
        adminTrainService.appendStation(id, dto);
        return Result.success();
    }

    @PostMapping("/{id}/stations/insert")
    public Result<Void> insertStation(@PathVariable Long id,
                                      @RequestParam int index,
                                      @RequestBody TrainStationReqDTO dto) {
        adminTrainService.insertStation(id, index, dto);
        return Result.success();
    }

    @DeleteMapping("/{id}/stations/{tsId}")
    public Result<Void> deleteStation(@PathVariable Long id,
                                      @PathVariable Long tsId) {
        adminTrainService.deleteStation(id, tsId);
        return Result.success();
    }

    // ==================== 克隆 ====================

    @PostMapping("/{id}/clone")
    public Result<Train> clone(@PathVariable Long id,
                                @RequestBody TrainCloneReqDTO reqDTO) {
        return Result.success(adminTrainService.clone(id, reqDTO));
    }

    // ==================== 车厢管理 ====================

    @GetMapping("/{id}/carriages")
    public Result<List<Carriage>> getCarriages(@PathVariable Long id) {
        return Result.success(adminTrainService.getCarriages(id));
    }

    @PostMapping("/{id}/carriage")
    public Result<Carriage> addCarriage(@PathVariable Long id,
                                         @RequestBody TrainCarriageReqDTO dto) {
        return Result.success(adminTrainService.addCarriage(id, dto));
    }

    @DeleteMapping("/{id}/carriage/{carriageId}")
    public Result<Void> deleteCarriage(@PathVariable Long id,
                                       @PathVariable Long carriageId) {
        adminTrainService.deleteCarriage(id, carriageId);
        return Result.success();
    }

    // ==================== 座位管理 ====================

    @GetMapping("/{id}/seats")
    public Result<List<Seat>> getSeats(@PathVariable Long id,
                                       @RequestParam(required = false) String carriageNumber) {
        return Result.success(adminTrainService.getSeats(id, carriageNumber));
    }

    @DeleteMapping("/{id}/seat/{seatId}")
    public Result<Void> deleteSeat(@PathVariable Long id,
                                   @PathVariable Long seatId) {
        adminTrainService.deleteSeat(id, seatId);
        return Result.success();
    }

    // ==================== 价格管理 ====================

    @GetMapping("/{id}/prices")
    public Result<List<TrainStationPrice>> getPrices(@PathVariable Long id) {
        return Result.success(adminTrainService.getPrices(id));
    }

    @PutMapping("/{id}/prices/batch")
    public Result<Void> batchUpdatePrices(@PathVariable Long id,
                                           @RequestBody TrainPriceBatchReqDTO reqDTO) {
        adminTrainService.batchUpdatePrices(id, reqDTO);
        return Result.success();
    }

    // ==================== 晚点 ====================

    @PostMapping("/{id}/delay")
    public Result<Void> delay(@PathVariable Long id,
                               @RequestBody TrainDelayReqDTO reqDTO) {
        adminTrainService.delay(id, reqDTO);
        return Result.success();
    }
}
