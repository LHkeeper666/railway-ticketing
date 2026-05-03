package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRespDTO {

    private String token;
    private Long userId;
    private String username;
    private String phone;
}
