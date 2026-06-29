package com.lhkeeper.ticketing.railway_ticketing.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 座位矩阵数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatMatrixDTO {

    /**
     * 座位矩阵 [row][col], 1=可用, 0=已占
     */
    private int[][] matrix;

    /**
     * 座位号映射: matrix[row][col] → seatNumber
     */
    private String[][] seatNumberMap;

    /**
     * 位置→列索引映射: {'A'→0, 'B'→1, 'C'→2, ...}
     */
    private Map<Character, Integer> posToIndex;

    /**
     * 列索引→位置映射: {0→'A', 1→'B', 2→'C', ...}
     */
    private Map<Integer, Character> indexToPos;

    /**
     * 矩阵维度
     */
    private int maxRow;
    private int maxCol;
}
