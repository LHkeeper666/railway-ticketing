package com.lhkeeper.ticketing.railway_ticketing.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistMatchMessageDTO {

    private Long trainId;

    private String startStation;

    private String endStation;
}
