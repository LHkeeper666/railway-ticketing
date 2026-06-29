package com.lhkeeper.ticketing.railway_ticketing.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 车厢信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarriageInfo {

    /**
     * 车厢号
     */
    private String carriageNumber;

    /**
     * 该车厢的座位矩阵
     */
    private SeatMatrixDTO matrix;

    /**
     * 可用座位数
     */
    private int availableCount;
}
