package com.lhkeeper.ticketing.railway_ticketing.util;

import org.springframework.stereotype.Component;

/**
 * 雪花算法 ID 生成器，生成全局唯一的长整型 ID
 */
@Component
public class SnowflakeUtil {

    private static final long EPOCH = 1609459200000L;
    private static final int NODE_BITS = 5;
    private static final int SEQUENCE_BITS = 7;

    private final long nodeID = 0;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

//    public SnowflakeUtil(long nodeID) {
//        this.nodeID = nodeID;
//    }

    /** 生成下一个唯一 ID，线程安全 */
    public synchronized long generateId() {
        long timestamp = System.currentTimeMillis() - EPOCH;
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID.");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & ((1 << SEQUENCE_BITS) - 1);
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return (timestamp << (NODE_BITS + SEQUENCE_BITS)) | (nodeID << SEQUENCE_BITS) | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis() - EPOCH;
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis() - EPOCH;
        }
        return timestamp;
    }
}
