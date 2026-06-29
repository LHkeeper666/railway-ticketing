package com.lhkeeper.ticketing.railway_ticketing.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SeatNumberParserTest {

    @Test
    void parseStandardSeatNumber() {
        int[] result = SeatNumberParser.parse("01A");
        assertEquals(1, result[0]);
        assertEquals('A', result[1]);
    }

    @Test
    void parseMultiDigitRow() {
        int[] result = SeatNumberParser.parse("12F");
        assertEquals(12, result[0]);
        assertEquals('F', result[1]);
    }

    @Test
    void parseAllPositions() {
        assertArrayEquals(new int[]{1, 'A'}, SeatNumberParser.parse("01A"));
        assertArrayEquals(new int[]{1, 'B'}, SeatNumberParser.parse("01B"));
        assertArrayEquals(new int[]{1, 'C'}, SeatNumberParser.parse("01C"));
        assertArrayEquals(new int[]{1, 'D'}, SeatNumberParser.parse("01D"));
        assertArrayEquals(new int[]{1, 'F'}, SeatNumberParser.parse("01F"));
    }

    @Test
    void parseInvalidSeatNumber() {
        assertThrows(IllegalArgumentException.class, () -> SeatNumberParser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> SeatNumberParser.parse(""));
        assertThrows(IllegalArgumentException.class, () -> SeatNumberParser.parse("A"));
    }

    @Test
    void getPositionIndex() {
        assertEquals(0, SeatNumberParser.getPositionIndex('A'));
        assertEquals(1, SeatNumberParser.getPositionIndex('B'));
        assertEquals(2, SeatNumberParser.getPositionIndex('C'));
        assertEquals(3, SeatNumberParser.getPositionIndex('D'));
        assertEquals(4, SeatNumberParser.getPositionIndex('F'));
    }

    @Test
    void getPositionIndexInvalid() {
        assertThrows(IllegalArgumentException.class, () -> SeatNumberParser.getPositionIndex('X'));
        assertThrows(IllegalArgumentException.class, () -> SeatNumberParser.getPositionIndex('E'));
    }

    @Test
    void isValidPosition() {
        assertTrue(SeatNumberParser.isValidPosition('A'));
        assertTrue(SeatNumberParser.isValidPosition('B'));
        assertTrue(SeatNumberParser.isValidPosition('C'));
        assertTrue(SeatNumberParser.isValidPosition('D'));
        assertTrue(SeatNumberParser.isValidPosition('F'));
        assertFalse(SeatNumberParser.isValidPosition('X'));
        assertFalse(SeatNumberParser.isValidPosition('E'));
    }
}
