package com.lhkeeper.ticketing.railway_ticketing.domain.dto.req;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeReqDTO {

    private String orderSn;

    /** 要改签的 ticket ID 列表 */
    private List<Long> ticketIds;

    /** 新车次 ID */
    private String newTrainId;

    /** 新出发站 */
    private String newStartStation;

    /** 新到达站 */
    private String newEndStation;

    /** 新座位类型（不填则沿用旧票类型） */
    private Integer newSeatType;

    /** 选座偏好（可选） */
    private List<String> chooseSeats;
}
