package com.lhkeeper.ticketing.railway_ticketing.service;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.ChangePasswordReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.UserUpdateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp.UserRespDTO;

public interface UserService {

    UserRespDTO getUserProfile();

    UserRespDTO updateProfile(UserUpdateReqDTO reqDTO);

    void changePassword(ChangePasswordReqDTO reqDTO);

    void deleteAccount(String password);
}
