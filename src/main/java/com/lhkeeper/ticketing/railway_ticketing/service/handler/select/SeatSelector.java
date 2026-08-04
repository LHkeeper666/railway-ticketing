package com.lhkeeper.ticketing.railway_ticketing.service.handler.select;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.OrderCreateReqDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Passenger;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.Seat;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStation;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStationPrice;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.exception.ServiceException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.PassengerMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.SeatMapper;
import com.lhkeeper.ticketing.railway_ticketing.mapper.TrainStationPriceMapper;
import com.lhkeeper.ticketing.railway_ticketing.service.TrainStationService;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLock;
import com.lhkeeper.ticketing.railway_ticketing.util.DistributedLockFactory;
import com.lhkeeper.ticketing.railway_ticketing.util.SeatNumberParser;
import com.lhkeeper.ticketing.railway_ticketing.util.StationCalculateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 座位选择器，基于位图模型选座并原子锁定
 */
@Component
@RequiredArgsConstructor
public class SeatSelector {

    private final PassengerMapper passengerMapper;
    private final SeatMapper seatMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final TrainStationService trainStationService;
    private final StringRedisTemplate stringRedisTemplate;
    private final DistributedLockFactory lockFactory;

    /** 普通购票选座，委托至 selectAndLockSeats */
    public List<TicketDTO> selectSeats(OrderCreateReqDTO orderCreateReqDTO) throws ServiceException {
        return selectAndLockSeats(
                Long.parseLong(orderCreateReqDTO.getTrainId()),
                orderCreateReqDTO.getStartStation(),
                orderCreateReqDTO.getEndStation(),
                orderCreateReqDTO.getPassengers(),
                orderCreateReqDTO.getChooseSeats()
        );
    }

    /** 抢票选座并锁定：支持智能选座和跨车厢 */
    public List<TicketDTO> selectAndLockSeats(Long trainId, String startStation, String endStation,
                                              List<OrderCreatePassengerDetailDTO> passengers,
                                              List<String> chooseSeats) throws ServiceException, ClientException {
        // 1. 获取乘客信息
        List<String> passengerIds = passengers.stream()
                .map(OrderCreatePassengerDetailDTO::getPassengerId).toList();

        List<Passenger> passengerDOs = passengerMapper.selectByIds(passengerIds);
        if (passengerDOs.isEmpty()) {
            throw new ClientException("无乘车人");
        }
        Map<Long, Passenger> idToPassenger = passengerDOs.stream()
                .collect(Collectors.toMap(
                        Passenger::getId,
                        Function.identity()
                ));

        // 2. 构建 TicketDTO 列表
        List<TicketDTO> ticketDTOList = new ArrayList<>();
        passengers.forEach(passenger -> {
            Passenger passengerDO = idToPassenger.get(Long.parseLong(passenger.getPassengerId()));
            ticketDTOList.add(TicketDTO.builder()
                    .seatType(passenger.getSeatType())
                    .passengerId(passenger.getPassengerId())
                    .phone(passengerDO.getPhone())
                    .idType(passengerDO.getIdType())
                    .idCard(passengerDO.getIdCard())
                    .realName(passengerDO.getRealName())
                    .userType(passengerDO.getDiscountType())
                    .build()
            );
        });

        // 3. 按座位类型分组 (同一订单只有一种座位类型)
        Integer seatType = passengers.get(0).getSeatType();
        int needCount = passengers.size();

        // 4. 计算区间掩码
        List<TrainStation> trainStations = trainStationService.getTrainStationsByTrainId(trainId);
        long purchaseMask = StationCalculateUtil.bitmapMask(trainStations, startStation, endStation);

        // 5. 获取分布式锁
        String lockKey = "seat:train:" + trainId + ":type:" + seatType;
        DistributedLock lock = lockFactory.tryLock(lockKey, 10);
        if (lock == null) {
            throw new ClientException("系统繁忙，请稍后重试");
        }

        try {
            // 6. 查询可用车厢的座位
            List<Seat> availableSeats = seatMapper.selectList(
                    Wrappers.lambdaQuery(Seat.class)
                            .eq(Seat::getTrainId, trainId)
                            .eq(Seat::getSeatType, seatType)
                            .apply("(seat_bitmap & {0}) = 0", purchaseMask)
            );

            if (availableSeats.size() < needCount) {
                throw new ClientException("余票不足");
            }

            // 7. 按车厢分组，构建内存矩阵
            Map<String, CarriageInfo> carriageInfoMap = buildCarriageInfoMap(availableSeats);

            // 8. 解析用户偏好
            List<Character> preferences = parsePreferences(chooseSeats);

            // 9. 智能选座算法
            List<SelectedSeatDTO> selectedSeats = selectSeats(carriageInfoMap, needCount, preferences);

            // 10. 批量锁定座位
            batchLockSeats(selectedSeats, trainId, purchaseMask);

            // 11. 填充 TicketDTO
            BigDecimal price = getPrice(trainId, startStation, endStation, seatType);
            fillTicketDTO(ticketDTOList, selectedSeats, price, purchaseMask);

            // 12. 删除余票缓存
            invalidateStockCache(trainId, startStation, endStation, trainStations);

            return ticketDTOList;

        } finally {
            lock.unlock();
        }
    }

    /**
     * 解析用户偏好
     *
     * @param chooseSeats 偏好列表 (如 ["A", "B", "C"])
     * @return 字符列表
     */
    private List<Character> parsePreferences(List<String> chooseSeats) {
        if (chooseSeats == null || chooseSeats.isEmpty()) {
            return null;
        }
        List<Character> preferences = new ArrayList<>();
        for (String seat : chooseSeats) {
            if (seat != null && !seat.isEmpty()) {
                preferences.add(seat.charAt(0));
            }
        }
        return preferences.isEmpty() ? null : preferences;
    }

    /**
     * 批量锁定座位
     *
     * @param selectedSeats 选中的座位列表
     * @param trainId       列车ID
     * @param purchaseMask  区间掩码
     */
    private void batchLockSeats(List<SelectedSeatDTO> selectedSeats, Long trainId, long purchaseMask) {
        for (SelectedSeatDTO selected : selectedSeats) {
            LambdaUpdateWrapper<Seat> lockWrapper = new LambdaUpdateWrapper<Seat>()
                    .eq(Seat::getTrainId, trainId)
                    .eq(Seat::getSeatNumber, selected.getSeatNumber())
                    .eq(Seat::getCarriageNumber, selected.getCarriageNumber())
                    .apply("(seat_bitmap & {0}) = 0", purchaseMask)
                    .setSql("seat_bitmap = seat_bitmap | " + purchaseMask);
            int updated = seatMapper.update(null, lockWrapper);
            if (updated == 0) {
                throw new ClientException("座位已被占用，请重新选座");
            }
        }
    }

    /**
     * 填充 TicketDTO 结果
     *
     * @param ticketDTOList  TicketDTO 列表
     * @param selectedSeats  选中的座位列表
     * @param price          票价
     */
    private void fillTicketDTO(List<TicketDTO> ticketDTOList, List<SelectedSeatDTO> selectedSeats, BigDecimal price, long purchaseMask) {
        for (int i = 0; i < ticketDTOList.size(); i++) {
            TicketDTO ticketDTO = ticketDTOList.get(i);
            SelectedSeatDTO selected = selectedSeats.get(i);
            ticketDTO.setSeatNumber(selected.getSeatNumber());
            ticketDTO.setCarriageNumber(selected.getCarriageNumber());
            ticketDTO.setAmount(price);
            ticketDTO.setPurchaseMask(purchaseMask);
        }
    }

    /**
     * 删除余票缓存
     */
    private void invalidateStockCache(Long trainId, String startStation, String endStation,
                                       List<TrainStation> trainStations) {
        String trainIdStr = String.valueOf(trainId);
        List<RouteDTO> cacheInvalidateRoutes = StationCalculateUtil.takeoutStation(
                trainStations, startStation, endStation);
        for (RouteDTO route : cacheInvalidateRoutes) {
            String stockCacheKey = String.format(RedisConstant.TICKET_STOCKING_MAPPING,
                    trainIdStr, route.getStartStation(), route.getEndStation());
            stringRedisTemplate.delete(stockCacheKey);
        }
    }

    private BigDecimal getPrice(Long trainId, String startStation, String endStation, Integer seatType) {
        TrainStationPrice priceRecord = trainStationPriceMapper.selectOne(
                Wrappers.lambdaQuery(TrainStationPrice.class)
                        .eq(TrainStationPrice::getTrainId, trainId)
                        .eq(TrainStationPrice::getStartStation, startStation)
                        .eq(TrainStationPrice::getEndStation, endStation)
                        .eq(TrainStationPrice::getSeatType, seatType)
        );
        return priceRecord != null ? BigDecimal.valueOf(priceRecord.getPrice()) : BigDecimal.ZERO;
    }

    /**
     * 构建座位矩阵
     *
     * @param seats 座位列表
     * @return 座位矩阵数据结构
     */
    public SeatMatrixDTO buildSeatMatrix(List<Seat> seats) {
        if (seats == null || seats.isEmpty()) {
            throw new IllegalArgumentException("Seats list cannot be empty");
        }

        // 1. 扫描数据，确定维度
        int maxRow = 0;
        Set<Character> positions = new TreeSet<>();
        for (Seat seat : seats) {
            int[] parsed = SeatNumberParser.parse(seat.getSeatNumber());
            maxRow = Math.max(maxRow, parsed[0]);
            positions.add((char) parsed[1]);
        }

        // 2. 构建位置索引映射
        Map<Character, Integer> posToIndex = new HashMap<>();
        Map<Integer, Character> indexToPos = new HashMap<>();
        int col = 0;
        for (char pos : positions) {
            posToIndex.put(pos, col);
            indexToPos.put(col, pos);
            col++;
        }

        // 3. 填充矩阵
        int[][] matrix = new int[maxRow][posToIndex.size()];
        String[][] seatNumberMap = new String[maxRow][posToIndex.size()];

        for (Seat seat : seats) {
            int[] parsed = SeatNumberParser.parse(seat.getSeatNumber());
            int row = parsed[0] - 1; // 转为0-based
            int colIdx = posToIndex.get((char) parsed[1]);
            matrix[row][colIdx] = 1; // 1=可用
            seatNumberMap[row][colIdx] = seat.getSeatNumber();
        }

        return SeatMatrixDTO.builder()
                .matrix(matrix)
                .seatNumberMap(seatNumberMap)
                .posToIndex(posToIndex)
                .indexToPos(indexToPos)
                .maxRow(maxRow)
                .maxCol(posToIndex.size())
                .build();
    }

    /**
     * 按车厢分组构建车厢信息Map
     *
     * @param seats 座位列表
     * @return 车厢号 → CarriageInfo
     */
    public Map<String, CarriageInfo> buildCarriageInfoMap(List<Seat> seats) {
        // 按车厢分组
        Map<String, List<Seat>> seatsByCarriage = seats.stream()
                .collect(Collectors.groupingBy(Seat::getCarriageNumber));

        Map<String, CarriageInfo> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Seat>> entry : seatsByCarriage.entrySet()) {
            String carriageNumber = entry.getKey();
            List<Seat> carriageSeats = entry.getValue();

            SeatMatrixDTO matrix = buildSeatMatrix(carriageSeats);
            int availableCount = 0;
            for (int[] row : matrix.getMatrix()) {
                for (int cell : row) {
                    if (cell == 1) {
                        availableCount++;
                    }
                }
            }

            result.put(carriageNumber, CarriageInfo.builder()
                    .carriageNumber(carriageNumber)
                    .matrix(matrix)
                    .availableCount(availableCount)
                    .build());
        }

        return result;
    }

    /**
     * 优先级1: 同排 + 满足偏好
     *
     * @param matrix      座位矩阵
     * @param preferences 用户偏好列表 (如 ['A', 'B', 'C'])
     * @return 选中的座位坐标列表 [row, col]，失败返回 null
     */
    public List<int[]> findAdjacentWithPreference(SeatMatrixDTO matrix, List<Character> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return null;
        }

        int[][] seatMatrix = matrix.getMatrix();
        Map<Character, Integer> posToIndex = matrix.getPosToIndex();

        // 将偏好转换为列索引
        List<Integer> prefCols = new ArrayList<>();
        for (char pref : preferences) {
            Integer colIdx = posToIndex.get(pref);
            if (colIdx == null) {
                return null; // 偏好位置不在当前座位类型中
            }
            prefCols.add(colIdx);
        }

        // 遍历每一排
        for (int row = 0; row < matrix.getMaxRow(); row++) {
            boolean allAvailable = true;
            for (int col : prefCols) {
                if (seatMatrix[row][col] == 0) {
                    allAvailable = false;
                    break;
                }
            }
            if (allAvailable) {
                // 找到满足条件的排
                List<int[]> result = new ArrayList<>();
                for (int col : prefCols) {
                    result.add(new int[]{row, col});
                }
                return result;
            }
        }

        return null;
    }

    /**
     * 优先级2: 同排连续座位
     *
     * @param matrix    座位矩阵
     * @param needCount 需要座位数
     * @return 选中的座位坐标列表 [row, col]，失败返回 null
     */
    public List<int[]> findAdjacent(SeatMatrixDTO matrix, int needCount) {
        int[][] seatMatrix = matrix.getMatrix();

        // 遍历每一排
        for (int row = 0; row < matrix.getMaxRow(); row++) {
            // 在该排找连续空位
            int consecutive = 0;
            int startCol = -1;
            for (int col = 0; col < matrix.getMaxCol(); col++) {
                if (seatMatrix[row][col] == 1) {
                    if (consecutive == 0) {
                        startCol = col;
                    }
                    consecutive++;
                    if (consecutive == needCount) {
                        // 找到连续空位
                        List<int[]> result = new ArrayList<>();
                        for (int i = 0; i < needCount; i++) {
                            result.add(new int[]{row, startCol + i});
                        }
                        return result;
                    }
                } else {
                    consecutive = 0;
                }
            }
        }

        return null;
    }

    /**
     * 优先级3: 同排分散座位
     *
     * @param matrix    座位矩阵
     * @param needCount 需要座位数
     * @return 选中的座位坐标列表 [row, col]，失败返回 null
     */
    public List<int[]> findSameRow(SeatMatrixDTO matrix, int needCount) {
        int[][] seatMatrix = matrix.getMatrix();

        // 遍历每一排
        for (int row = 0; row < matrix.getMaxRow(); row++) {
            List<int[]> available = new ArrayList<>();
            for (int col = 0; col < matrix.getMaxCol(); col++) {
                if (seatMatrix[row][col] == 1) {
                    available.add(new int[]{row, col});
                    if (available.size() == needCount) {
                        return available;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 优先级4: 任意可用座位
     *
     * @param matrix    座位矩阵
     * @param needCount 需要座位数
     * @return 选中的座位坐标列表 [row, col]，失败返回 null
     */
    public List<int[]> findAny(SeatMatrixDTO matrix, int needCount) {
        int[][] seatMatrix = matrix.getMatrix();
        List<int[]> result = new ArrayList<>();

        for (int row = 0; row < matrix.getMaxRow(); row++) {
            for (int col = 0; col < matrix.getMaxCol(); col++) {
                if (seatMatrix[row][col] == 1) {
                    result.add(new int[]{row, col});
                    if (result.size() == needCount) {
                        return result;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 在单个车厢内选座
     *
     * @param carriageInfo 车厢信息
     * @param needCount    需要座位数
     * @param preferences  用户偏好 (可为 null)
     * @return 选中的座位列表，失败返回 null
     */
    public List<SelectedSeatDTO> findSeatsInCarriage(CarriageInfo carriageInfo, int needCount, List<Character> preferences) {
        SeatMatrixDTO matrix = carriageInfo.getMatrix();
        String carriageNumber = carriageInfo.getCarriageNumber();

        // 优先级1: 同排 + 满足偏好
        if (preferences != null && !preferences.isEmpty()) {
            List<int[]> seats = findAdjacentWithPreference(matrix, preferences);
            if (seats != null) {
                return convertToSelectedSeats(seats, matrix, carriageNumber);
            }
        }

        // 优先级2: 同排 + 连续
        List<int[]> seats = findAdjacent(matrix, needCount);
        if (seats != null) {
            return convertToSelectedSeats(seats, matrix, carriageNumber);
        }

        // 优先级3: 同排 + 分散
        seats = findSameRow(matrix, needCount);
        if (seats != null) {
            return convertToSelectedSeats(seats, matrix, carriageNumber);
        }

        // 优先级4: 同车厢 + 分散
        seats = findAny(matrix, needCount);
        if (seats != null) {
            return convertToSelectedSeats(seats, matrix, carriageNumber);
        }

        return null;
    }

    /**
     * 将矩阵坐标转换为 SelectedSeatDTO
     *
     * @param seats          座位坐标列表 [row, col]
     * @param matrix         座位矩阵
     * @param carriageNumber 车厢号
     * @return SelectedSeatDTO 列表
     */
    private List<SelectedSeatDTO> convertToSelectedSeats(List<int[]> seats, SeatMatrixDTO matrix, String carriageNumber) {
        List<SelectedSeatDTO> result = new ArrayList<>();
        for (int[] seat : seats) {
            int row = seat[0];
            int col = seat[1];
            String seatNumber = matrix.getSeatNumberMap()[row][col];
            result.add(SelectedSeatDTO.builder()
                    .seatNumber(seatNumber)
                    .carriageNumber(carriageNumber)
                    .row(row)
                    .col(col)
                    .build());
        }
        return result;
    }

    /**
     * 主选座方法: 单车厢优先，失败则跨车厢
     *
     * @param carriageInfoMap 车厢信息Map
     * @param needCount       需要座位数
     * @param preferences     用户偏好 (可为 null)
     * @return 选中的座位列表
     */
    public List<SelectedSeatDTO> selectSeats(Map<String, CarriageInfo> carriageInfoMap, int needCount, List<Character> preferences) {
        // 优先级1: 尝试单车厢选座
        for (CarriageInfo carriageInfo : carriageInfoMap.values()) {
            if (carriageInfo.getAvailableCount() >= needCount) {
                List<SelectedSeatDTO> result = findSeatsInCarriage(carriageInfo, needCount, preferences);
                if (result != null) {
                    return result;
                }
            }
        }

        // 优先级2: 跨车厢选座
        return selectAcrossCarriages(carriageInfoMap, needCount, preferences);
    }

    /**
     * 跨车厢选座
     *
     * @param carriageInfoMap 车厢信息Map
     * @param needCount       需要座位数
     * @param preferences     用户偏好 (可为 null)
     * @return 选中的座位列表
     */
    private List<SelectedSeatDTO> selectAcrossCarriages(Map<String, CarriageInfo> carriageInfoMap, int needCount, List<Character> preferences) {
        // 按可用座位数降序排序
        List<CarriageInfo> sortedCarriages = carriageInfoMap.values().stream()
                .sorted((a, b) -> Integer.compare(b.getAvailableCount(), a.getAvailableCount()))
                .collect(Collectors.toList());

        List<SelectedSeatDTO> result = new ArrayList<>();
        int remaining = needCount;
        List<Character> currentPrefs = preferences;

        for (CarriageInfo carriageInfo : sortedCarriages) {
            if (remaining == 0) break;

            int available = carriageInfo.getAvailableCount();
            if (available == 0) continue;

            int toAllocate = Math.min(remaining, available);

            // 在该车厢内选座
            List<SelectedSeatDTO> seats = findSeatsInCarriage(carriageInfo, toAllocate, currentPrefs);
            if (seats != null && !seats.isEmpty()) {
                result.addAll(seats);
                remaining -= seats.size();
                currentPrefs = null; // 后续车厢不再考虑偏好
            }
        }

        if (remaining > 0) {
            throw new ClientException("余票不足");
        }

        return result;
    }
}
