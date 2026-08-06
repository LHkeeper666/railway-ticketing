package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.ChangePasswordReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import org.springframework.stereotype.Component;

@Component
public class ChangePasswordParamNotNullChainHandler implements ChangePasswordChainFilter<ChangePasswordReqDTO> {

    @Override
    public void handler(ChangePasswordReqDTO requestParam) {
        if (requestParam == null) {
            throw new ClientException("参数不能为空");
        }
        if (StringUtil.isBlank(requestParam.getOldPassword())) {
            throw new ClientException("旧密码不能为空");
        }
        if (StringUtil.isBlank(requestParam.getNewPassword())) {
            throw new ClientException("新密码不能为空");
        }
        if (StringUtil.isBlank(requestParam.getConfirmPassword())) {
            throw new ClientException("确认密码不能为空");
        }
        if (!requestParam.getNewPassword().equals(requestParam.getConfirmPassword())) {
            throw new ClientException("两次输入的密码不一致");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
