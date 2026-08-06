package com.lhkeeper.ticketing.railway_ticketing.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lhkeeper.ticketing.railway_ticketing.context.UserContext;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.ChangePasswordReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.UserUpdateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.UserRespDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.User;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.ChainMarkEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.UserMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.UserService;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.filter.AbstractChainContext;
import com.lhkeeper.ticketing.railway_ticketing.util.AesUtil;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AesUtil aesUtil;
    private final AbstractChainContext<UserUpdateReqDTO> userUpdateChainContext;
    private final AbstractChainContext<ChangePasswordReqDTO> changePasswordChainContext;
    private final AbstractChainContext<String> userDeleteChainContext;

    @Override
    public UserRespDTO getUserProfile() {
        Long userId = UserContext.get().getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ClientException("用户不存在");
        }
        return toRespDTO(user);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public UserRespDTO updateProfile(UserUpdateReqDTO reqDTO) {
        userUpdateChainContext.handler(ChainMarkEnum.USER_UPDATE.name(), reqDTO);

        Long userId = UserContext.get().getUserId();
        User user = new User();
        user.setId(userId);

        BeanUtils.copyProperties(reqDTO, user, "idCard");

//        if (reqDTO.getRealName() != null) {
//            user.setRealName(reqDTO.getRealName());
//        }
//        if (reqDTO.getIdType() != null) {
//            user.setIdType(reqDTO.getIdType());
//        }
        if (StringUtil.isNotBlank(reqDTO.getIdCard())) {
            user.setIdCard(aesUtil.encrypt(reqDTO.getIdCard()));
        }
//        if (reqDTO.getMail() != null) {
//            user.setMail(reqDTO.getMail());
//        }
//        if (reqDTO.getRegion() != null) {
//            user.setRegion(reqDTO.getRegion());
//        }
//        if (reqDTO.getAddress() != null) {
//            user.setAddress(reqDTO.getAddress());
//        }
//        if (reqDTO.getTelephone() != null) {
//            user.setTelephone(reqDTO.getTelephone());
//        }
//        if (reqDTO.getPostCode() != null) {
//            user.setPostCode(reqDTO.getPostCode());
//        }
//        if (reqDTO.getUserType() != null) {
//            user.setUserType(reqDTO.getUserType());
//        }

        userMapper.updateById(user);

        User updated = userMapper.selectById(userId);
        return toRespDTO(updated);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void changePassword(ChangePasswordReqDTO reqDTO) {
        changePasswordChainContext.handler(ChainMarkEnum.CHANGE_PASSWORD.name(), reqDTO);

        Long userId = UserContext.get().getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ClientException("用户不存在");
        }

        if (!passwordEncoder.matches(reqDTO.getOldPassword(), user.getPassword())) {
            throw new ClientException("旧密码错误");
        }

        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setPassword(passwordEncoder.encode(reqDTO.getNewPassword()));
        userMapper.updateById(updateUser);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void deleteAccount(String password) {
        userDeleteChainContext.handler(ChainMarkEnum.USER_DELETE.name(), password);

        Long userId = UserContext.get().getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new ClientException("用户不存在");
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ClientException("密码错误");
        }

        userMapper.update(null,
                Wrappers.<User>lambdaUpdate()
                        .set(User::getDelFlag, 1)
                        .set(User::getDeletionTime, System.currentTimeMillis())
                        .eq(User::getId, userId));
    }

    private UserRespDTO toRespDTO(User user) {
        String rawIdCard = aesUtil.decrypt(user.getIdCard());
        return UserRespDTO.builder()
                .username(user.getUsername())
                .realName(user.getRealName())
                .phone(aesUtil.maskPhone(user.getPhone()))
                .idType(user.getIdType())
                .idCard(aesUtil.maskIdCard(rawIdCard))
                .mail(user.getMail())
                .region(user.getRegion())
                .address(user.getAddress())
                .userType(user.getUserType())
                .verifyStatus(user.getVerifyStatus())
                .telephone(user.getTelephone())
                .postCode(user.getPostCode())
                .build();
    }
}
