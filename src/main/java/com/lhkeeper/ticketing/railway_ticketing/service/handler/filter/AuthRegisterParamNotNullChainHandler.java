package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.RegisterReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import org.springframework.stereotype.Component;

/**
 * 注册参数非空校验（order 0）
 */
@Component
public class AuthRegisterParamNotNullChainHandler implements AuthRegisterChainFilter<RegisterReqDTO> {

    @Override
    public void handler(RegisterReqDTO requestParam) {
        if (requestParam == null) {
            throw new ClientException("参数不能为空");
        }
        if (StringUtil.isBlank(requestParam.getPhone())) {
            throw new ClientException("手机号不能为空");
        }
        if (StringUtil.isBlank(requestParam.getPassword())) {
            throw new ClientException("密码不能为空");
        }
        if (StringUtil.isBlank(requestParam.getUsername())) {
            throw new ClientException("用户名不能为空");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
