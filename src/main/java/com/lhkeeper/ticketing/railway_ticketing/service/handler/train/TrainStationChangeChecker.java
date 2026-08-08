package com.lhkeeper.ticketing.railway_ticketing.service.handler.train;

import java.util.ArrayList;
import java.util.List;

/**
 * 列车路线变更安全检查器。
 * 比较新旧车站列表，判断变更类型，为路线修改的安全策略提供决策依据。
 */
public class TrainStationChangeChecker {

    public enum ChangeType {
        /** 仅元数据变化（时间、名称），段结构不变 */
        METADATA_ONLY,
        /** 末尾追加 */
        APPEND,
        /** 末尾删除 */
        DELETE_END,
        /** 中间插入 */
        INSERT_MIDDLE,
        /** 中间删除 */
        DELETE_MIDDLE,
        /** 重排或混合变更 */
        REORDER
    }

    /**
     * 检测新旧路线之间的变更类型。
     *
     * @param oldStationNames 旧路线站名序列（按 sequence 排序）
     * @param newStationNames 新路线站名序列
     * @return 变更类型
     */
    public static ChangeType detect(List<String> oldStationNames, List<String> newStationNames) {
        int oldSize = oldStationNames.size();
        int newSize = newStationNames.size();

        if (oldSize == newSize) {
            // 大小相同，检查是否只是元数据变更（站名序列一致）
            if (oldStationNames.equals(newStationNames)) {
                return ChangeType.METADATA_ONLY;
            }
            // 大小相同但站名不同 → 重排或修改
            return ChangeType.REORDER;
        }

        if (newSize > oldSize) {
            // 新路线更长 → 检查是否是末尾追加
            List<String> prefix = newStationNames.subList(0, oldSize);
            if (prefix.equals(oldStationNames)) {
                return ChangeType.APPEND;
            }
            return ChangeType.INSERT_MIDDLE;
        }

        // 新路线更短 → 检查是否是末尾删除
        List<String> retained = new ArrayList<>(oldStationNames);
        retained.remove(newSize); // remove the station at newSize index (the first deleted one)
        // simpler: check if newStationNames equals oldStationNames.subList(0, newSize)
        if (newStationNames.equals(oldStationNames.subList(0, newSize))) {
            return ChangeType.DELETE_END;
        }
        return ChangeType.DELETE_MIDDLE;
    }

    /**
     * 判断变更是否安全（在有活跃订单的情况下）。
     */
    public static boolean isSafeWithActiveOrders(ChangeType changeType) {
        return changeType == ChangeType.METADATA_ONLY
                || changeType == ChangeType.APPEND;
    }

    /**
     * 判断是否允许原地修改（不经过克隆）。
     */
    public static boolean isAllowedInPlace(ChangeType changeType, boolean hasActiveOrders) {
        if (!hasActiveOrders) return true;
        return isSafeWithActiveOrders(changeType);
    }
}
