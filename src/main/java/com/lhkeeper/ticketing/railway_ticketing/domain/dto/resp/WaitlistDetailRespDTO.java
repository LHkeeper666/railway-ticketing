package com.lhkeeper.ticketing.railway_ticketing.domain.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistDetailRespDTO {

    private String waitlistSn;

    private Integer status;

    /** 队列位置（从 0 开始，-1 表示已不在队列中） */
    private Long queuePosition;

    private LocalDateTime expireTime;

    private String trainNumber;

    private String startStation;

    private String endStation;

    private Integer seatType;

    private List<PassengerInfo> passengers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PassengerInfo {
        private Long passengerId;
        private String realName;
        private String seatPreference;
    }
}
