package com.lhkeeper.ticketing.railway_ticketing.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 座位号解析工具类
 */
public final class SeatNumberParser {

    private static final Map<Character, Integer> POSITION_MAP = new HashMap<>();

    static {
        POSITION_MAP.put('A', 0);
        POSITION_MAP.put('B', 1);
        POSITION_MAP.put('C', 2);
        POSITION_MAP.put('D', 3);
        POSITION_MAP.put('F', 4);
    }

    private SeatNumberParser() {
    }

    /**
     * 解析座位号
     *
     * @param seatNumber 座位号，如 "01A", "12F"
     * @return [row(1-based), positionChar]
     */
    public static int[] parse(String seatNumber) {
        if (seatNumber == null || seatNumber.length() < 2) {
            throw new IllegalArgumentException("Invalid seat number: " + seatNumber);
        }
        char position = seatNumber.charAt(seatNumber.length() - 1);
        String rowStr = seatNumber.substring(0, seatNumber.length() - 1);
        int row = Integer.parseInt(rowStr);
        return new int[]{row, position};
    }

    /**
     * 获取位置索引
     *
     * @param position 位置字符 (A/B/C/D/F)
     * @return 列索引
     */
    public static int getPositionIndex(char position) {
        Integer index = POSITION_MAP.get(position);
        if (index == null) {
            throw new IllegalArgumentException("Invalid position: " + position);
        }
        return index;
    }

    /**
     * 检查位置字符是否有效
     *
     * @param position 位置字符
     * @return 是否有效
     */
    public static boolean isValidPosition(char position) {
        return POSITION_MAP.containsKey(position);
    }
}
