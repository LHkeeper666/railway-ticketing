package com.lhkeeper.ticketing.railway_ticketing.service.handler.filter;

import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import org.springframework.stereotype.Component;

@Component
public class UserDeleteParamNotNullChainHandler implements UserDeleteChainFilter {

    @Override
    public void handler(String password) {
        if (StringUtil.isBlank(password)) {
            throw new ClientException("密码不能为空");
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
