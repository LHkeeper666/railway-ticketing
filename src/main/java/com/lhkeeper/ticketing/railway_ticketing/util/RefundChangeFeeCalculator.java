package com.lhkeeper.ticketing.railway_ticketing.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 退票/改签手续费计算器，参考 12306 阶梯规则。
 */
public final class RefundChangeFeeCalculator {

    private RefundChangeFeeCalculator() {}

    /** 退票手续费率：>48h 免费，24-48h 10%，<24h 20% */
    private static final BigDecimal REFUND_RATE_MID = new BigDecimal("0.10");
    private static final BigDecimal REFUND_RATE_HIGH = new BigDecimal("0.20");

    /** 改签手续费率：>48h 免费，24-48h 5%，<24h 15% */
    private static final BigDecimal CHANGE_RATE_MID = new BigDecimal("0.05");
    private static final BigDecimal CHANGE_RATE_HIGH = new BigDecimal("0.15");

    /**
     * 计算退票手续费
     *
     * @param ticketPrice    单张票票价
     * @param departureTime  列车出发时间
     * @param now            当前时间
     * @return 手续费金额
     * @throws IllegalArgumentException 如果列车已出发
     */
    public static BigDecimal calculateRefundFee(BigDecimal ticketPrice, LocalDateTime departureTime, LocalDateTime now) {
        long minutesUntilDeparture = Duration.between(now, departureTime).toMinutes();
        if (minutesUntilDeparture <= 0) {
            throw new IllegalArgumentException("列车已出发，不可退票");
        }
        if (minutesUntilDeparture > 48 * 60) {
            return BigDecimal.ZERO;
        }
        if (minutesUntilDeparture > 24 * 60) {
            return ticketPrice.multiply(REFUND_RATE_MID).setScale(2, RoundingMode.HALF_UP);
        }
        return ticketPrice.multiply(REFUND_RATE_HIGH).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算改签手续费
     *
     * @param totalAmount    改签票总面额
     * @param departureTime  原列车出发时间
     * @param now            当前时间
     * @return 手续费金额
     * @throws IllegalArgumentException 如果列车已出发
     */
    public static BigDecimal calculateChangeFee(BigDecimal totalAmount, LocalDateTime departureTime, LocalDateTime now) {
        long minutesUntilDeparture = Duration.between(now, departureTime).toMinutes();
        if (minutesUntilDeparture <= 0) {
            throw new IllegalArgumentException("列车已出发，不可改签");
        }
        if (minutesUntilDeparture > 48 * 60) {
            return BigDecimal.ZERO;
        }
        if (minutesUntilDeparture > 24 * 60) {
            return totalAmount.multiply(CHANGE_RATE_MID).setScale(2, RoundingMode.HALF_UP);
        }
        return totalAmount.multiply(CHANGE_RATE_HIGH).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 校验退票/改签时间是否合法
     *
     * @return 距离开车的分钟数，<=0 表示已出发
     */
    public static long minutesUntilDeparture(LocalDateTime departureTime, LocalDateTime now) {
        return Duration.between(now, departureTime).toMinutes();
    }
}
