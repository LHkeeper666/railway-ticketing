package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.Data;

@Data
public class TrainDelayReqDTO {

    /** 晚点分钟数：正数=晚点，负数=早点 */
    private int delayMinutes;
}
