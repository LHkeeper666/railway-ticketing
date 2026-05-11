package com.lhkeeper.ticketing.railway_ticketing.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁工厂，基于 Redis setIfAbsent + Lua 原子解锁。
 * 后续迁移 Redisson 时只需替换此类实现，所有调用点无需改动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockFactory {

    private final StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setScriptText("""
                if redis.call('GET', KEYS[1]) == ARGV[1] then
                    return redis.call('DEL', KEYS[1])
                else
                    return 0
                end
                """);
    }

    /**
     * 尝试获取锁，成功返回 DistributedLock 对象，失败返回 null。
     *
     * @param key        锁键（不含前缀，工厂内部拼接 lock: 前缀）
     * @param ttlSeconds 锁超时秒数
     */
    public DistributedLock tryLock(String key, long ttlSeconds) {
        String lockId = UUID.randomUUID().toString();
        String fullKey = "lock:" + key;
        boolean acquired = Boolean.TRUE.equals(
                stringRedisTemplate.opsForValue().setIfAbsent(fullKey, lockId, ttlSeconds, TimeUnit.SECONDS));
        if (!acquired) {
            return null;
        }
        return new SimpleDistributedLock(stringRedisTemplate, fullKey, lockId);
    }

    private record SimpleDistributedLock(StringRedisTemplate redis, String key,
                                         String lockId) implements DistributedLock {

        @Override
        public void unlock() {
            Long result = redis.execute(UNLOCK_SCRIPT, Collections.singletonList(key), lockId);
            if (result != null && result == 1L) {
                log.debug("锁已释放, key={}", key);
            } else {
                log.debug("锁已过期或被其他实例持有, key={}", key);
            }
        }
    }
}
