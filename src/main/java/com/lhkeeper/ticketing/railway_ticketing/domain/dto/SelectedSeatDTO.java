package com.lhkeeper.ticketing.railway_ticketing.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 选中的座位信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectedSeatDTO {

    /**
     * 座位号 (如 "01A")
     */
    private String seatNumber;

    /**
     * 车厢号 (如 "05")
     */
    private String carriageNumber;

    /**
     * 矩阵行号 (0-based)
     */
    private int row;

    /**
     * 矩阵列号 (0-based)
     */
    private int col;

    /**
     * 分配的乘客ID
     */
    private String passengerId;
}
