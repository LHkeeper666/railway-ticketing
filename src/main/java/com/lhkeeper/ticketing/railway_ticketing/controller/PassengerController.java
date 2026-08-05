package com.lhkeeper.ticketing.railway_ticketing.controller;

import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.PassengerUpdateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.PassengerRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.service.PassengerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/passenger")
@RequiredArgsConstructor
public class PassengerController {

    private final PassengerService passengerService;

    @PostMapping
    public Result<PassengerRespDTO> create(@RequestBody PassengerCreateReqDTO reqDTO) {
        return Result.success(passengerService.create(reqDTO));
    }

    @PutMapping("/{id}")
    public Result<PassengerRespDTO> update(@PathVariable Long id,
                                           @RequestBody PassengerUpdateReqDTO reqDTO) {
        return Result.success(passengerService.update(id, reqDTO));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        passengerService.delete(id);
        return Result.success();
    }

    @GetMapping("/list")
    public Result<List<PassengerRespDTO>> listMine() {
        return Result.success(passengerService.listMine());
    }
}
