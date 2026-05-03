package com.lhkeeper.ticketing.railway_ticketing.common.constant;

public final class RedisConstant {

    /**
     * 区域code与区域name映射
     */
    public static final String REGION_CODE_TO_REGION_NAME_MAPPING = "region-code-to-region-name:%s";

    /**
     * 区间车次查询：Key prefix + 起始地区_终点地区
     */
    public static final String TRAIN_STATION_RELATION_MAPPING = "train-station-relation-mapping:%s_%s";

    /**
     * 区间车次 id -> trainStationRelation 映射
     */
    public static final String TRAINID_TO_STATION_RELATION_MAPPING = "train-station-relation-mapping:%s";

    /**
     * 查询train信息：trainId -> train entity
     */
    public static final String TRAINID_TO_TRAIN_MAPPING = "train-info:%s";

    /**
     * 查询余票信息：trainId + startStation + endStation -> List<Seat>
     */
    public static final String TICKET_STOCKING_MAPPING = "ticket-stocking-mapping:%s_%s_%s";

    /**
     * 列车区间信息：trainId + startStation -> TrainStation
     */
    public static final String TRAIN_STATION_MAPPING = "train-station:%s_%s";
}
