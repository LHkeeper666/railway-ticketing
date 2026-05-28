package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.LoginReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RegisterReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.LoginRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.User;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.UserMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.AuthService;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import com.lhkeeper.ticketing.railway_ticketing.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现，处理登录验证、JWT 签发和用户注册
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AbstractChainContext<LoginReqDTO> loginChainContext;
    private final AbstractChainContext<RegisterReqDTO> registerChainContext;

    /**
     * 用户登录：责任链校验参数 → 查用户 → 验密码 → 签发 JWT
     */
    @Override
    public LoginRespDTO login(LoginReqDTO reqDTO) {
        loginChainContext.handler(ChainMarkEnum.AUTH_LOGIN.name(), reqDTO);
        String phone = reqDTO.getPhone();
        String password = reqDTO.getPassword();
        User user = userMapper.selectOne(
                Wrappers.lambdaQuery(User.class).eq(User::getPhone, phone)
        );
        if (user == null) {
            throw new ClientException("手机号未注册");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ClientException("密码错误");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getPhone());
        return LoginRespDTO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .phone(user.getPhone())
                .build();
    }

    /**
     * 用户注册：责任链校验参数 → 手机号唯一性检查 → 密码加密 → 入库。
     * uk_phone 唯一约束兜底防并发注册同一手机号。
     */
    @Override
    public void register(RegisterReqDTO reqDTO) {
        registerChainContext.handler(ChainMarkEnum.AUTH_REGISTER.name(), reqDTO);
        String phone = reqDTO.getPhone();
        boolean exists = userMapper.exists(
                Wrappers.lambdaQuery(User.class).eq(User::getPhone, phone)
        );
        if (exists) {
            throw new ClientException("手机号已注册");
        }
        User user = new User();
        user.setPhone(phone);
        user.setPassword(passwordEncoder.encode(reqDTO.getPassword()));
        user.setUsername(reqDTO.getUsername());
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ClientException("手机号已注册");
        }
    }
}
