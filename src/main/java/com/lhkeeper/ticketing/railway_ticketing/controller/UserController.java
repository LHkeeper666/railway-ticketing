package com.lhkeeper.ticketing.railway_ticketing.controller;

import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.ChangePasswordReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.UserUpdateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.UserRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<UserRespDTO> profile() {
        return Result.success(userService.getUserProfile());
    }

    @PutMapping("/update")
    public Result<UserRespDTO> update(@RequestBody UserUpdateReqDTO reqDTO) {
        return Result.success(userService.updateProfile(reqDTO));
    }

    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody ChangePasswordReqDTO reqDTO) {
        try {
            userService.changePassword(reqDTO);
            return Result.success();
        } catch (ClientException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestBody Map<String, String> body) {
        try {
            String password = body.get("password");
            userService.deleteAccount(password);
            return Result.success();
        } catch (ClientException e) {
            return Result.fail(e.getMessage());
        }
    }
}
