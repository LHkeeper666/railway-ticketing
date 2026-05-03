package com.lhkeeper.ticketing.railway_ticketing.common.constant;

public final class RedisConstant {

    /*
     * ======================== Key 模板 ========================
     */

    /** 区域code与区域name映射 */
    public static final String REGION_CODE_TO_REGION_NAME_MAPPING = "region-code-to-region-name:%s";

    /** 区间车次查询：Key prefix + 起始地区_终点地区 */
    public static final String TRAIN_STATION_RELATION_MAPPING = "train-station-relation-mapping:%s_%s";

    /** 区间车次 id -> trainStationRelation 映射 */
    public static final String TRAINID_TO_STATION_RELATION_MAPPING = "train-station-relation-mapping:%s";

    /** 查询train信息：trainId -> train entity */
    public static final String TRAINID_TO_TRAIN_MAPPING = "train-info:%s";

    /** 查询余票信息：trainId + startStation + endStation -> List<Seat> */
    public static final String TICKET_STOCKING_MAPPING = "ticket-stocking-mapping:%s_%s_%s";

    /** 列车区间信息：trainId + startStation -> TrainStation */
    public static final String TRAIN_STATION_MAPPING = "train-station:%s_%s";

    /** Region 缓存加载标记 */
    public static final String REGION_LOADED_FLAG = "cache:region:loaded";

    /** 空值缓存占位符 */
    public static final String NULL_PLACEHOLDER = "{}";

    /*
     * ======================== 缓存 TTL（秒） ========================
     */

    /** 区域映射 1h */
    public static final long CACHE_TTL_REGION = 3600L;
    /** 车次信息 30min */
    public static final long CACHE_TTL_TRAIN_INFO = 1800L;
    /** 车次关系 10min */
    public static final long CACHE_TTL_STATION_RELATION = 600L;
    /** 余票库存 1min（高频变化） */
    public static final long CACHE_TTL_SEAT_STOCK = 60L;
    /** 列车区间 1h */
    public static final long CACHE_TTL_TRAIN_STATION = 3600L;
    /** 空值缓存 30s */
    public static final long CACHE_TTL_NULL = 30L;

    /*
     * ======================== 分布式锁 ========================
     */

    /** 锁 key 前缀 */
    public static final String LOCK_KEY_PREFIX = "lock:";
    /** 锁超时时间（秒） */
    public static final long LOCK_TTL_SECONDS = 10L;
}
