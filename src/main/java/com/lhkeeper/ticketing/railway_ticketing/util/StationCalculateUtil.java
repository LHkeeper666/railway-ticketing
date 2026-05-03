package com.lhkeeper.ticketing.railway_ticketing.util;

import com.lhkeeper.ticketing.railway_ticketing.domain.dto.RouteDTO;
import com.lhkeeper.ticketing.railway_ticketing.domain.entity.TrainStation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class StationCalculateUtil {

    private StationCalculateUtil() {}

    /**
     * 计算与购买区间重叠的全部区间（用于锁定/释放座位）
     *
     * @param stations   列车区间列表（按 sequence 升序）
     * @param departure  出发站
     * @param arrival    到达站
     * @return 所有重叠的区间，包含购买区间自身
     */
    public static List<RouteDTO> takeoutStation(List<TrainStation> stations, String departure, String arrival) {
        List<TrainStation> sorted = new ArrayList<>(stations);
        sorted.sort(Comparator.comparing(TrainStation::getSequence));

        List<String> stationNames = sorted.stream()
                .map(TrainStation::getStartStation)
                .toList();

        int depIdx = stationNames.indexOf(departure);
        int arrIdx = stationNames.indexOf(arrival);
        if (arrIdx == -1) {
            // arrival 可能是某个区间的 endStation（如始发站的到达站）
            arrIdx = findEndStationIdx(sorted, arrival) + 1;
        }

        if (depIdx == -1 || arrIdx == -1 || depIdx >= arrIdx) {
            throw new IllegalArgumentException(
                    "出发站或到达站不在列车路线中: " + departure + " -> " + arrival);
        }

        int n = stationNames.size();
        List<RouteDTO> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (i < arrIdx && j > depIdx) {
                    result.add(new RouteDTO(stationNames.get(i), stationNames.get(j)));
                }
            }
        }
        return result;
    }

    /**
     * 拆分购买区间为相邻站点段
     */
    public static List<RouteDTO> throughStation(List<TrainStation> stations, String departure, String arrival) {
        List<TrainStation> sorted = new ArrayList<>(stations);
        sorted.sort(Comparator.comparing(TrainStation::getSequence));

        List<String> stationNames = sorted.stream()
                .map(TrainStation::getStartStation)
                .toList();

        int depIdx = stationNames.indexOf(departure);
        int arrIdx = stationNames.indexOf(arrival);
        if (arrIdx == -1) {
            arrIdx = findEndStationIdx(sorted, arrival) + 1;
        }

        if (depIdx == -1 || arrIdx == -1 || depIdx >= arrIdx) {
            throw new IllegalArgumentException(
                    "出发站或到达站不在列车路线中: " + departure + " -> " + arrival);
        }

        List<RouteDTO> result = new ArrayList<>();
        for (int i = depIdx; i < arrIdx; i++) {
            result.add(new RouteDTO(stationNames.get(i), stationNames.get(i + 1)));
        }
        return result;
    }

    private static int findEndStationIdx(List<TrainStation> stations, String stationName) {
        for (int i = 0; i < stations.size(); i++) {
            if (stationName.equals(stations.get(i).getEndStation())) {
                return i;
            }
        }
        return -1;
    }
}
