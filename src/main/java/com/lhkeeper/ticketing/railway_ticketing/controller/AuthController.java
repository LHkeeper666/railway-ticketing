package com.lhkeeper.ticketing.railway_ticketing.controller;

import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.LoginReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RegisterReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.LoginRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginRespDTO> login(@RequestBody LoginReqDTO reqDTO) {
        return Result.success(authService.login(reqDTO));
    }

    @PostMapping("/register")
    public Result<Void> register(@RequestBody RegisterReqDTO reqDTO) {
        authService.register(reqDTO);
        return Result.success();
    }
}
