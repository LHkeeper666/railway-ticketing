package com.lhkeeper.ticketing.railway_ticketing.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.common.page.PageResponse;
import com.lhkeeper.ticketing.railway_ticketing.domain.dto.req.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.*;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.OrderStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.domain.enums.SeatStatusEnum;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import com.lhkeeper.ticketing.railway_ticketing.mapper.*;
import com.lhkeeper.ticketing.railway_ticketing.service.admin.AdminTrainService;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.train.TrainStationChangeChecker;
import com.lhkeeper.ticketing.railway_ticketing.service.handler.train.TrainStationRelationGenerator;
import com.lhkeeper.ticketing.railway_ticketing.util.SnowflakeUtil;
import com.lhkeeper.ticketing.railway_ticketing.util.StringUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminTrainServiceImpl implements AdminTrainService {

    private final TrainMapper trainMapper;
    private final TrainStationMapper trainStationMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final CarriageMapper carriageMapper;
    private final SeatMapper seatMapper;
    private final OrderMapper orderMapper;
    private final StationMapper stationMapper;
    private final TrainStationRelationGenerator relationGenerator;
    private final SnowflakeUtil snowflakeUtil;
    private final StringRedisTemplate stringRedisTemplate;

    // ==================== 列车基本信息 ====================

    @Override
    public PageResponse<Train> page(long current, long size, String trainNumber, Integer trainType) {
        LambdaQueryWrapper<Train> wrapper = new LambdaQueryWrapper<Train>()
                .orderByDesc(Train::getDepartureTime);
        if (!StringUtil.isBlank(trainNumber)) {
            wrapper.like(Train::getTrainNumber, trainNumber);
        }
        if (trainType != null) {
            wrapper.eq(Train::getTrainType, trainType);
        }
        Page<Train> page = new Page<>(current, size);
        IPage<Train> result = trainMapper.selectPage(page, wrapper);
        return PageResponse.from(result, result.getRecords());
    }

    @Override
    public Train getDetail(Long id) {
        Train train = trainMapper.selectById(id);
        if (train == null) throw new ClientException("列车不存在");
        return train;
    }

    @Override
    public List<TrainStation> getStations(Long id) {
        getDetail(id);
        return trainStationMapper.selectList(
                new LambdaQueryWrapper<TrainStation>()
                        .eq(TrainStation::getTrainId, id)
                        .orderByAsc(TrainStation::getSequence)
        );
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Train create(Train train) {
        train.setSaleStatus(0);
        trainMapper.insert(train);
        clearTrainCache(train.getId());
        return train;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Train updateMeta(Long id, Train update) {
        Train existing = getDetail(id);

        // 如果有活跃订单，时间变更需要用 delay 接口
        boolean timesChanged = !Objects.equals(existing.getDepartureTime(), update.getDepartureTime())
                || !Objects.equals(existing.getArrivalTime(), update.getArrivalTime());
        if (timesChanged && hasActiveOrders(id)) {
            throw new ClientException("该车次有未完成订单，时间变更请使用晚点接口 POST /admin/train/{id}/delay");
        }

        update.setId(id);
        update.setSaleStatus(existing.getSaleStatus());
        trainMapper.updateById(update);
        clearTrainCache(id);
        return update;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void delete(Long id) {
        getDetail(id);
        long orderCount = orderMapper.selectCount(
                new LambdaQueryWrapper<Order>().eq(Order::getTrainId, id)
        );
        if (orderCount > 0) {
            // 软删除
            Train train = new Train();
            train.setId(id);
            train.setDelFlag(1);
            train.setSaleStatus(1);
            trainMapper.updateById(train);
            clearTrainCache(id);
            throw new ClientException("该车次有 " + orderCount + " 个历史订单，已软删除");
        }
        // 物理删除
        trainStationMapper.delete(new LambdaQueryWrapper<TrainStation>().eq(TrainStation::getTrainId, id));
        trainStationRelationMapper.delete(new LambdaQueryWrapper<TrainStationRelation>().eq(TrainStationRelation::getTrainId, id));
        trainStationPriceMapper.delete(new LambdaQueryWrapper<TrainStationPrice>().eq(TrainStationPrice::getTrainId, id));
        seatMapper.delete(new LambdaQueryWrapper<Seat>().eq(Seat::getTrainId, id));
        carriageMapper.delete(new LambdaQueryWrapper<Carriage>().eq(Carriage::getTrainId, id));
        trainMapper.deleteById(id);
        clearTrainCache(id);
    }

    // ==================== 路线管理 ====================

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void setStations(Long trainId, List<TrainStationReqDTO> stationDTOs) {
        getDetail(trainId);
        if (stationDTOs.isEmpty()) throw new ClientException("站点列表不能为空");

        List<TrainStation> oldStations = getStations(trainId);
        boolean hasOrders = hasActiveOrders(trainId);

        if (!hasOrders) {
            // 无订单 → 全量替换
            replaceStations(trainId, stationDTOs, oldStations);
            return;
        }

        // 有订单 → 安全检查
        List<String> oldNames = oldStations.stream().map(TrainStation::getStartStation).collect(Collectors.toList());
        List<String> newNames = stationDTOs.stream().map(TrainStationReqDTO::getStationName).collect(Collectors.toList());
        TrainStationChangeChecker.ChangeType changeType = TrainStationChangeChecker.detect(oldNames, newNames);

        if (!TrainStationChangeChecker.isAllowedInPlace(changeType, true)) {
            throw new ClientException("该车次有未完成订单，" + getChangeTypeMessage(changeType)
                    + "会导致座位位图错乱。请使用克隆功能 POST /admin/train/" + trainId + "/clone");
        }
        replaceStations(trainId, stationDTOs, oldStations);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void appendStation(Long trainId, TrainStationReqDTO dto) {
        List<TrainStation> stations = getStations(trainId);
        List<TrainStationReqDTO> newList = new ArrayList<>();
        for (TrainStation ts : stations) {
            TrainStationReqDTO s = new TrainStationReqDTO();
            s.setStationName(ts.getStartStation());
            s.setRegionName(ts.getStartRegion());
            s.setDepartureTime(ts.getDepartureTime());
            s.setArrivalTime(ts.getArrivalTime());
            s.setStopoverTime(ts.getStopoverTime());
            newList.add(s);
        }
        newList.add(dto);
        setStations(trainId, newList);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void insertStation(Long trainId, int index, TrainStationReqDTO dto) {
        List<TrainStation> stations = getStations(trainId);
        List<TrainStationReqDTO> newList = new ArrayList<>();
        for (int i = 0; i < stations.size(); i++) {
            if (i == index) newList.add(dto);
            TrainStation ts = stations.get(i);
            TrainStationReqDTO s = new TrainStationReqDTO();
            s.setStationName(ts.getStartStation());
            s.setRegionName(ts.getStartRegion());
            s.setDepartureTime(ts.getDepartureTime());
            s.setArrivalTime(ts.getArrivalTime());
            s.setStopoverTime(ts.getStopoverTime());
            newList.add(s);
        }
        if (index >= stations.size()) newList.add(dto);
        setStations(trainId, newList);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void deleteStation(Long trainId, Long tsId) {
        TrainStation target = trainStationMapper.selectById(tsId);
        if (target == null || !target.getTrainId().equals(trainId)) {
            throw new ClientException("站点不存在");
        }
        List<TrainStation> stations = getStations(trainId);
        List<TrainStationReqDTO> newList = new ArrayList<>();
        for (TrainStation ts : stations) {
            if (ts.getId().equals(tsId)) continue;
            TrainStationReqDTO s = new TrainStationReqDTO();
            s.setStationName(ts.getStartStation());
            s.setRegionName(ts.getStartRegion());
            s.setDepartureTime(ts.getDepartureTime());
            s.setArrivalTime(ts.getArrivalTime());
            s.setStopoverTime(ts.getStopoverTime());
            newList.add(s);
        }
        setStations(trainId, newList);
    }

    private void replaceStations(Long trainId, List<TrainStationReqDTO> dtoList, List<TrainStation> oldStations) {
        // 删除旧数据
        trainStationMapper.delete(new LambdaQueryWrapper<TrainStation>().eq(TrainStation::getTrainId, trainId));
        trainStationRelationMapper.delete(new LambdaQueryWrapper<TrainStationRelation>().eq(TrainStationRelation::getTrainId, trainId));

        // 重置座位状态
        List<Seat> seats = seatMapper.selectList(new LambdaQueryWrapper<Seat>().eq(Seat::getTrainId, trainId));
        for (Seat seat : seats) {
            seat.setSeatBitmap(0L);
            seat.setSeatStatus(SeatStatusEnum.AVAILABLE.getCode());
            seatMapper.updateById(seat);
        }

        // 插入新 TrainStation 记录
        List<TrainStation> newStations = new ArrayList<>();
        for (int i = 0; i < dtoList.size(); i++) {
            TrainStationReqDTO dto = dtoList.get(i);
            TrainStation ts = new TrainStation();
            ts.setTrainId(trainId);
            ts.setStartStation(dto.getStationName());
            ts.setStartRegion(dto.getRegionName());
            ts.setDepartureTime(dto.getDepartureTime());
            ts.setArrivalTime(dto.getArrivalTime());
            ts.setStopoverTime(dto.getStopoverTime() != null ? dto.getStopoverTime() : 0);
            ts.setSequence(String.format("%02d", i + 1));

            if (i + 1 < dtoList.size()) {
                ts.setEndStation(dtoList.get(i + 1).getStationName());
                ts.setEndRegion(dtoList.get(i + 1).getRegionName());
            }
            // 找 station_id
            Station station = stationMapper.selectOne(
                    new LambdaQueryWrapper<Station>().eq(Station::getStationName, dto.getStationName())
            );
            ts.setStationId(station != null ? station.getId() : null);

            trainStationMapper.insert(ts);
            newStations.add(ts);
        }

        // 重新生成 relation
        relationGenerator.regenerate(trainId, newStations);
        clearTrainCache(trainId);
    }

    // ==================== 克隆列车 ====================

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Train clone(Long sourceTrainId, TrainCloneReqDTO reqDTO) {
        Train source = getDetail(sourceTrainId);
        Long newTrainId = snowflakeUtil.generateId();

        // 克隆 Train
        Train newTrain = new Train();
        newTrain.setId(newTrainId);
        newTrain.setTrainNumber(reqDTO.getTrainNumber());
        newTrain.setTrainType(source.getTrainType());
        newTrain.setTrainTag(source.getTrainTag());
        newTrain.setTrainBrand(source.getTrainBrand());
        newTrain.setSaleTime(source.getSaleTime());
        newTrain.setSaleStatus(0);
        newTrain.setDepartureTime(source.getDepartureTime());
        newTrain.setArrivalTime(source.getArrivalTime());

        // 决定用哪个路线
        List<TrainStationReqDTO> stationDTOs = reqDTO.getStations();
        List<TrainStation> sourceStations;
        if (stationDTOs != null && !stationDTOs.isEmpty()) {
            // 用新路线计算 start/end station
            newTrain.setStartStation(stationDTOs.get(0).getStationName());
            newTrain.setEndStation(stationDTOs.get(stationDTOs.size() - 1).getStationName());
            newTrain.setStartRegion(stationDTOs.get(0).getRegionName());
            newTrain.setEndRegion(stationDTOs.get(stationDTOs.size() - 1).getRegionName());
            newTrain.setDepartureTime(stationDTOs.get(0).getDepartureTime());
            newTrain.setArrivalTime(stationDTOs.get(stationDTOs.size() - 1).getArrivalTime());
            sourceStations = buildTrainStations(newTrainId, stationDTOs);
        } else {
            newTrain.setStartStation(source.getStartStation());
            newTrain.setEndStation(source.getEndStation());
            newTrain.setStartRegion(source.getStartRegion());
            newTrain.setEndRegion(source.getEndRegion());
            List<TrainStation> oldStations = getStations(sourceTrainId);
            stationDTOs = new ArrayList<>();
            for (TrainStation ts : oldStations) {
                TrainStationReqDTO dto = new TrainStationReqDTO();
                dto.setStationName(ts.getStartStation());
                dto.setRegionName(ts.getStartRegion());
                dto.setDepartureTime(ts.getDepartureTime());
                dto.setArrivalTime(ts.getArrivalTime());
                dto.setStopoverTime(ts.getStopoverTime());
                stationDTOs.add(dto);
            }
            sourceStations = buildTrainStations(newTrainId, stationDTOs);
        }

        trainMapper.insert(newTrain);

        // 克隆车厢
        List<Carriage> sourceCarriages = carriageMapper.selectList(
                new LambdaQueryWrapper<Carriage>().eq(Carriage::getTrainId, sourceTrainId)
        );
        Map<String, List<Seat>> sourceSeatsByCarriage = new LinkedHashMap<>();
        for (Carriage sc : sourceCarriages) {
            Carriage nc = new Carriage();
            nc.setTrainId(newTrainId);
            nc.setCarriageNumber(sc.getCarriageNumber());
            nc.setCarriageType(sc.getCarriageType());
            nc.setSeatCount(sc.getSeatCount());
            carriageMapper.insert(nc);

            List<Seat> carriageSeats = seatMapper.selectList(
                    new LambdaQueryWrapper<Seat>()
                            .eq(Seat::getTrainId, sourceTrainId)
                            .eq(Seat::getCarriageNumber, sc.getCarriageNumber())
            );
            sourceSeatsByCarriage.put(sc.getCarriageNumber(), carriageSeats);
        }

        // 克隆座位（重置 bitmap 和 status）
        for (var entry : sourceSeatsByCarriage.entrySet()) {
            for (Seat ss : entry.getValue()) {
                Seat ns = new Seat();
                ns.setTrainId(newTrainId);
                ns.setCarriageNumber(entry.getKey());
                ns.setSeatNumber(ss.getSeatNumber());
                ns.setSeatType(ss.getSeatType());
                ns.setSeatBitmap(0L);
                ns.setSeatStatus(SeatStatusEnum.AVAILABLE.getCode());
                ns.setPrice(ss.getPrice());
                seatMapper.insert(ns);
            }
        }

        // 生成新路线关系
        relationGenerator.regenerate(newTrainId, sourceStations);

        // 复制价格（同站名的）
        List<TrainStationPrice> sourcePrices = trainStationPriceMapper.selectList(
                new LambdaQueryWrapper<TrainStationPrice>().eq(TrainStationPrice::getTrainId, sourceTrainId)
        );
        for (TrainStationPrice sp : sourcePrices) {
            TrainStationPrice np = new TrainStationPrice();
            np.setTrainId(newTrainId);
            np.setStartStation(sp.getStartStation());
            np.setEndStation(sp.getEndStation());
            np.setSeatType(sp.getSeatType());
            np.setPrice(sp.getPrice());
            trainStationPriceMapper.insert(np);
        }

        // 冻结旧车
        Train freeze = new Train();
        freeze.setId(sourceTrainId);
        freeze.setSaleStatus(1);
        trainMapper.updateById(freeze);

        clearTrainCache(sourceTrainId);
        clearTrainCache(newTrainId);
        return newTrain;
    }

    // ==================== 车厢/座位管理 ====================

    @Override
    public List<Carriage> getCarriages(Long trainId) {
        return carriageMapper.selectList(
                new LambdaQueryWrapper<Carriage>().eq(Carriage::getTrainId, trainId)
        );
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public Carriage addCarriage(Long trainId, TrainCarriageReqDTO dto) {
        Carriage c = new Carriage();
        c.setTrainId(trainId);
        c.setCarriageNumber(dto.getCarriageNumber());
        c.setCarriageType(dto.getCarriageType());
        c.setSeatCount(dto.getSeatCount());
        carriageMapper.insert(c);

        // 批量生成座位
        for (int i = 1; i <= dto.getSeatCount(); i++) {
            Seat s = new Seat();
            s.setTrainId(trainId);
            s.setCarriageNumber(dto.getCarriageNumber());
            s.setSeatNumber(formatSeatNumber(i));
            s.setSeatType(dto.getCarriageType());
            s.setSeatBitmap(0L);
            s.setSeatStatus(SeatStatusEnum.AVAILABLE.getCode());
            s.setPrice(BigDecimal.ZERO);
            seatMapper.insert(s);
        }
        return c;
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void deleteCarriage(Long trainId, Long carriageId) {
        Carriage c = carriageMapper.selectById(carriageId);
        if (c == null || !c.getTrainId().equals(trainId)) throw new ClientException("车厢不存在");
        seatMapper.delete(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getTrainId, trainId)
                .eq(Seat::getCarriageNumber, c.getCarriageNumber())
        );
        carriageMapper.deleteById(carriageId);
    }

    @Override
    public List<Seat> getSeats(Long trainId, String carriageNumber) {
        LambdaQueryWrapper<Seat> wrapper = new LambdaQueryWrapper<Seat>()
                .eq(Seat::getTrainId, trainId)
                .orderByAsc(Seat::getCarriageNumber)
                .orderByAsc(Seat::getSeatNumber);
        if (!StringUtil.isBlank(carriageNumber)) {
            wrapper.eq(Seat::getCarriageNumber, carriageNumber);
        }
        return seatMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void deleteSeat(Long trainId, Long seatId) {
        Seat s = seatMapper.selectById(seatId);
        if (s == null || !s.getTrainId().equals(trainId)) throw new ClientException("座位不存在");
        seatMapper.deleteById(seatId);
    }

    // ==================== 价格管理 ====================

    @Override
    public List<TrainStationPrice> getPrices(Long trainId) {
        return trainStationPriceMapper.selectList(
                new LambdaQueryWrapper<TrainStationPrice>().eq(TrainStationPrice::getTrainId, trainId)
        );
    }

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void batchUpdatePrices(Long trainId, TrainPriceBatchReqDTO reqDTO) {
        for (var item : reqDTO.getPrices()) {
            TrainStationPrice existing = trainStationPriceMapper.selectOne(
                    new LambdaQueryWrapper<TrainStationPrice>()
                            .eq(TrainStationPrice::getTrainId, trainId)
                            .eq(TrainStationPrice::getStartStation, item.getStartStation())
                            .eq(TrainStationPrice::getEndStation, item.getEndStation())
                            .eq(TrainStationPrice::getSeatType, item.getSeatType())
            );
            if (existing != null) {
                existing.setPrice(item.getPrice());
                trainStationPriceMapper.updateById(existing);
            } else {
                TrainStationPrice np = new TrainStationPrice();
                np.setTrainId(trainId);
                np.setStartStation(item.getStartStation());
                np.setEndStation(item.getEndStation());
                np.setSeatType(item.getSeatType());
                np.setPrice(item.getPrice());
                trainStationPriceMapper.insert(np);
            }
        }
    }

    // ==================== 晚点 ====================

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public void delay(Long trainId, TrainDelayReqDTO reqDTO) {
        Train train = getDetail(trainId);
        int minutes = reqDTO.getDelayMinutes();

        train.setDepartureTime(shift(train.getDepartureTime(), minutes));
        train.setArrivalTime(shift(train.getArrivalTime(), minutes));
        trainMapper.updateById(train);

        List<TrainStation> stations = getStations(trainId);
        for (TrainStation ts : stations) {
            if (ts.getDepartureTime() != null) ts.setDepartureTime(shift(ts.getDepartureTime(), minutes));
            if (ts.getArrivalTime() != null) ts.setArrivalTime(shift(ts.getArrivalTime(), minutes));
            trainStationMapper.updateById(ts);
        }

        List<TrainStationRelation> relations = trainStationRelationMapper.selectList(
                new LambdaQueryWrapper<TrainStationRelation>().eq(TrainStationRelation::getTrainId, trainId)
        );
        for (TrainStationRelation r : relations) {
            if (r.getDepartureTime() != null) r.setDepartureTime(shift(r.getDepartureTime(), minutes));
            if (r.getArrivalTime() != null) r.setArrivalTime(shift(r.getArrivalTime(), minutes));
            trainStationRelationMapper.updateById(r);
        }

        clearTrainCache(trainId);
    }

    // ==================== 辅助方法 ====================

    private boolean hasActiveOrders(Long trainId) {
        return orderMapper.selectCount(
                new LambdaQueryWrapper<Order>()
                        .eq(Order::getTrainId, trainId)
                        .in(Order::getStatus, Arrays.asList(
                                OrderStatusEnum.UNPAID.getCode(),
                                OrderStatusEnum.PAID.getCode(),
                                OrderStatusEnum.PENDING.getCode()
                        ))
        ) > 0;
    }

    private List<TrainStation> buildTrainStations(Long trainId, List<TrainStationReqDTO> dtoList) {
        List<TrainStation> result = new ArrayList<>();
        for (int i = 0; i < dtoList.size(); i++) {
            TrainStationReqDTO dto = dtoList.get(i);
            TrainStation ts = new TrainStation();
            ts.setTrainId(trainId);
            ts.setStartStation(dto.getStationName());
            ts.setStartRegion(dto.getRegionName());
            ts.setDepartureTime(dto.getDepartureTime());
            ts.setArrivalTime(dto.getArrivalTime());
            ts.setStopoverTime(dto.getStopoverTime() != null ? dto.getStopoverTime() : 0);
            ts.setSequence(String.format("%02d", i + 1));
            if (i + 1 < dtoList.size()) {
                ts.setEndStation(dtoList.get(i + 1).getStationName());
                ts.setEndRegion(dtoList.get(i + 1).getRegionName());
            }
            Station station = stationMapper.selectOne(
                    new LambdaQueryWrapper<Station>().eq(Station::getStationName, dto.getStationName())
            );
            ts.setStationId(station != null ? station.getId() : null);
            trainStationMapper.insert(ts);
            result.add(ts);
        }
        return result;
    }

    private void clearTrainCache(Long trainId) {
        String trainInfoKey = String.format(RedisConstant.TRAINID_TO_TRAIN_MAPPING, trainId);
        stringRedisTemplate.delete(trainInfoKey);
        String stationListKey = String.format(RedisConstant.TRAIN_STATION_LIST, trainId);
        stringRedisTemplate.delete(stationListKey);
    }

    private LocalDateTime shift(LocalDateTime dt, int minutes) {
        return dt != null ? dt.plusMinutes(minutes) : null;
    }

    private String formatSeatNumber(int index) {
        int row = (index - 1) / 5 + 1;
        char col = (char) ('A' + (index - 1) % 5);
        return String.format("%02d%c", row, col);
    }

    private String getChangeTypeMessage(TrainStationChangeChecker.ChangeType type) {
        return switch (type) {
            case INSERT_MIDDLE -> "中间插入停站";
            case DELETE_MIDDLE -> "中间删除停站";
            case DELETE_END -> "末尾删除停站";
            case REORDER -> "调整停站顺序";
            default -> "路线变更";
        };
    }
}
