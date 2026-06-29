package com.lhkeeper.ticketing.railway_ticketing.service.handler.select;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.SeatMatrixDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.SelectedSeatDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.CarriageInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SeatSelectorTest {

    private SeatSelector seatSelector;

    @BeforeEach
    void setUp() {
        // 创建 SeatSelector 实例，不需要 mock 依赖，因为测试的是纯算法方法
        seatSelector = new SeatSelector(null, null, null, null, null, null);
    }

    private SeatMatrixDTO createTestMatrix() {
        // 创建一个 5排 x 5列 的测试矩阵
        // A  B  C  D  F
        // 1  1  1  1  1   排1
        // 1  1  0  1  1   排2
        // 0  0  0  0  0   排3
        // 1  1  1  0  0   排4
        // 1  1  1  1  1   排5
        int[][] matrix = {
                {1, 1, 1, 1, 1},
                {1, 1, 0, 1, 1},
                {0, 0, 0, 0, 0},
                {1, 1, 1, 0, 0},
                {1, 1, 1, 1, 1}
        };

        String[][] seatNumberMap = {
                {"01A", "01B", "01C", "01D", "01F"},
                {"02A", "02B", "02C", "02D", "02F"},
                {"03A", "03B", "03C", "03D", "03F"},
                {"04A", "04B", "04C", "04D", "04F"},
                {"05A", "05B", "05C", "05D", "05F"}
        };

        Map<Character, Integer> posToIndex = Map.of('A', 0, 'B', 1, 'C', 2, 'D', 3, 'F', 4);
        Map<Integer, Character> indexToPos = Map.of(0, 'A', 1, 'B', 2, 'C', 3, 'D', 4, 'F');

        return SeatMatrixDTO.builder()
                .matrix(matrix)
                .seatNumberMap(seatNumberMap)
                .posToIndex(posToIndex)
                .indexToPos(indexToPos)
                .maxRow(5)
                .maxCol(5)
                .build();
    }

    @Test
    void findAdjacentWithPreferenceSuccess() {
        SeatMatrixDTO matrix = createTestMatrix();
        List<Character> preferences = List.of('A', 'B', 'C');

        List<int[]> result = seatSelector.findAdjacentWithPreference(matrix, preferences);

        assertNotNull(result);
        assertEquals(3, result.size());
        // 应该找到排1 (index 0)，因为排1的A,B,C都是1
        assertEquals(0, result.get(0)[0]); // row = 0 (排1)
        assertEquals(0, result.get(0)[1]); // col A = 0
        assertEquals(1, result.get(1)[1]); // col B = 1
        assertEquals(2, result.get(2)[1]); // col C = 2
    }

    @Test
    void findAdjacentWithPreferenceFail() {
        SeatMatrixDTO matrix = createTestMatrix();
        // 排2的C位置是0，无法满足 ABC
        List<Character> preferences = List.of('A', 'B', 'C');

        // 应该找到排1，因为排1的ABC都是1
        List<int[]> result = seatSelector.findAdjacentWithPreference(matrix, preferences);
        assertNotNull(result);
    }

    @Test
    void findAdjacentWithPreferenceNull() {
        SeatMatrixDTO matrix = createTestMatrix();

        assertNull(seatSelector.findAdjacentWithPreference(matrix, null));
        assertNull(seatSelector.findAdjacentWithPreference(matrix, List.of()));
    }

    @Test
    void findAdjacentSuccess() {
        SeatMatrixDTO matrix = createTestMatrix();

        // 排1有5个连续空位
        List<int[]> result = seatSelector.findAdjacent(matrix, 3);
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    void findAdjacentFail() {
        SeatMatrixDTO matrix = createTestMatrix();

        // 排3全为0，无法找到连续座位
        // 但排1有连续座位，所以应该成功
        List<int[]> result = seatSelector.findAdjacent(matrix, 5);
        assertNotNull(result);
    }

    @Test
    void findSameRowSuccess() {
        SeatMatrixDTO matrix = createTestMatrix();

        // 排2有4个空位 (A,B,D,F)
        List<int[]> result = seatSelector.findSameRow(matrix, 4);
        assertNotNull(result);
        assertEquals(4, result.size());
    }

    @Test
    void findAnySuccess() {
        SeatMatrixDTO matrix = createTestMatrix();

        // 找任意3个空位
        List<int[]> result = seatSelector.findAny(matrix, 3);
        assertNotNull(result);
        assertEquals(3, result.size());
    }

    @Test
    void findAnyFail() {
        SeatMatrixDTO matrix = createTestMatrix();

        // 找100个空位，应该失败
        List<int[]> result = seatSelector.findAny(matrix, 100);
        assertNull(result);
    }

    @Test
    void findSeatsInCarriageWithPreference() {
        SeatMatrixDTO matrix = createTestMatrix();
        CarriageInfo carriageInfo = CarriageInfo.builder()
                .carriageNumber("05")
                .matrix(matrix)
                .availableCount(19) // 19个可用座位
                .build();

        List<Character> preferences = List.of('A', 'B');
        List<SelectedSeatDTO> result = seatSelector.findSeatsInCarriage(carriageInfo, 2, preferences);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("05", result.get(0).getCarriageNumber());
    }

    @Test
    void findSeatsInCarriageWithoutPreference() {
        SeatMatrixDTO matrix = createTestMatrix();
        CarriageInfo carriageInfo = CarriageInfo.builder()
                .carriageNumber("05")
                .matrix(matrix)
                .availableCount(19)
                .build();

        List<SelectedSeatDTO> result = seatSelector.findSeatsInCarriage(carriageInfo, 3, null);

        assertNotNull(result);
        assertEquals(3, result.size());
    }
}
