package com.lhkeeper.ticketing.railway_ticketing.domain.statemachine;

/**
 * 状态转移结果。不可变值对象，封装 CAS 更新结果和 DB 最新状态。
 */
public class TransitResult {

    private final boolean success;
    private final Integer currentStatus;

    private TransitResult(boolean success, Integer currentStatus) {
        this.success = success;
        this.currentStatus = currentStatus;
    }

    public static TransitResult success(Integer status) {
        return new TransitResult(true, status);
    }

    public static TransitResult conflict(Integer actualStatus) {
        return new TransitResult(false, actualStatus);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isConflict() {
        return !success;
    }

    /**
     * 始终返回 DB 最新状态值。success=true 时等于 targetTo，conflict 时等于当前实际值。
     */
    public Integer getCurrentStatus() {
        return currentStatus;
    }
}
