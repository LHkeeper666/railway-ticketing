package com.lhkeeper.ticketing.railway_ticketing.util;

/**
 * 分布式锁对象，对标 Redisson RLock，后续迁移只需替换工厂实现。
 */
public interface DistributedLock {

    /** 释放锁，内部通过 Lua 脚本原子校验归属 */
    void unlock();
}
