package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerRespDTO {

    private Long id;
    private String realName;
    private Integer idType;
    private String idCard;
    private String phone;
    private Integer discountType;
    private Integer verifyStatus;
    private String createDate;
}
