package com.lhkeeper.ticketing.railway_ticketing.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RefundChangeFeeCalculatorTest {

    // ==================== 退票手续费 ====================

    @Test
    void refundFee_greaterThan48h_shouldBeFree() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 8, 0); // 72h before
        BigDecimal fee = RefundChangeFeeCalculator.calculateRefundFee(
                new BigDecimal("100.00"), departure, now);
        assertEquals(0, fee.compareTo(BigDecimal.ZERO));
    }

    @Test
    void refundFee_24to48h_shouldBe10Percent() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 9, 0, 0); // 32h before
        BigDecimal fee = RefundChangeFeeCalculator.calculateRefundFee(
                new BigDecimal("100.00"), departure, now);
        assertEquals(0, fee.compareTo(new BigDecimal("10.00")));
    }

    @Test
    void refundFee_lessThan24h_shouldBe20Percent() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 9, 20, 0); // 12h before
        BigDecimal fee = RefundChangeFeeCalculator.calculateRefundFee(
                new BigDecimal("100.00"), departure, now);
        assertEquals(0, fee.compareTo(new BigDecimal("20.00")));
    }

    @Test
    void refundFee_afterDeparture_shouldThrow() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        assertThrows(IllegalArgumentException.class, () ->
                RefundChangeFeeCalculator.calculateRefundFee(
                        new BigDecimal("100.00"), departure, now));
    }

    @Test
    void refundFee_exactly48h_shouldBe10Percent() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 8, 8, 0); // exactly 48h
        BigDecimal fee = RefundChangeFeeCalculator.calculateRefundFee(
                new BigDecimal("100.00"), departure, now);
        assertEquals(0, fee.compareTo(new BigDecimal("10.00")));
    }

    @Test
    void refundFee_exactly24h_shouldBe20Percent() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 9, 8, 0); // exactly 24h
        BigDecimal fee = RefundChangeFeeCalculator.calculateRefundFee(
                new BigDecimal("100.00"), departure, now);
        assertEquals(0, fee.compareTo(new BigDecimal("20.00")));
    }

    @ParameterizedTest
    @CsvSource({
            "500.00, 72, 0",         // >48h: free
            "500.00, 36, 50.00",     // 24-48h: 10%
            "500.00, 6,  100.00",    // <24h: 20%
            "333.33, 30, 33.33",     // rounding test
    })
    void refundFee_parameterized(String price, long hoursBefore, String expectedFee) {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = departure.minusHours(hoursBefore);
        BigDecimal fee = RefundChangeFeeCalculator.calculateRefundFee(
                new BigDecimal(price), departure, now);
        assertEquals(0, fee.compareTo(new BigDecimal(expectedFee)));
    }

    // ==================== 改签手续费 ====================

    @Test
    void changeFee_greaterThan48h_shouldBeFree() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 7, 8, 0); // 72h before
        BigDecimal fee = RefundChangeFeeCalculator.calculateChangeFee(
                new BigDecimal("100.00"), departure, now);
        assertEquals(0, fee.compareTo(BigDecimal.ZERO));
    }

    @Test
    void changeFee_24to48h_shouldBe5Percent() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 9, 0, 0); // 32h before
        BigDecimal fee = RefundChangeFeeCalculator.calculateChangeFee(
                new BigDecimal("100.00"), departure, now);
        assertEquals(0, fee.compareTo(new BigDecimal("5.00")));
    }

    @Test
    void changeFee_lessThan24h_shouldBe15Percent() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 9, 20, 0); // 12h before
        BigDecimal fee = RefundChangeFeeCalculator.calculateChangeFee(
                new BigDecimal("100.00"), departure, now);
        assertEquals(0, fee.compareTo(new BigDecimal("15.00")));
    }

    @Test
    void changeFee_afterDeparture_shouldThrow() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        assertThrows(IllegalArgumentException.class, () ->
                RefundChangeFeeCalculator.calculateChangeFee(
                        new BigDecimal("100.00"), departure, now));
    }

    // ==================== 辅助方法 ====================

    @Test
    void minutesUntilDeparture_beforeDeparture_shouldBePositive() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 6, 0);
        long minutes = RefundChangeFeeCalculator.minutesUntilDeparture(departure, now);
        assertEquals(120, minutes);
    }

    @Test
    void minutesUntilDeparture_afterDeparture_shouldBeNegative() {
        LocalDateTime departure = LocalDateTime.of(2026, 8, 10, 8, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 9, 0);
        long minutes = RefundChangeFeeCalculator.minutesUntilDeparture(departure, now);
        assertTrue(minutes < 0);
    }
}
