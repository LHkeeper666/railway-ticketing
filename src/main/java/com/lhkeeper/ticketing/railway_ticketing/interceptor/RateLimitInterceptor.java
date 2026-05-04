package com.lhkeeper.ticketing.railway_ticketing.interceptor;

import com.lhkeeper.ticketing.railway_ticketing.common.annotation.RateLimit;
import com.lhkeeper.ticketing.railway_ticketing.common.constant.RedisConstant;
import com.lhkeeper.ticketing.railway_ticketing.common.result.Result;
import com.lhkeeper.ticketing.railway_ticketing.exception.ClientException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT;

    static {
        TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>();
        TOKEN_BUCKET_SCRIPT.setResultType(Long.class);
        TOKEN_BUCKET_SCRIPT.setScriptText("""
                local key = KEYS[1]
                local capacity = tonumber(ARGV[1])
                local refillRate = tonumber(ARGV[2])
                local now = tonumber(ARGV[3])

                local tokens = tonumber(redis.call('HGET', key, 'tokens'))
                local lastRefill = tonumber(redis.call('HGET', key, 'last_refill'))

                if tokens == nil then
                    tokens = capacity
                    lastRefill = now
                end

                local elapsed = (now - lastRefill) / 1000.0
                local refill = elapsed * refillRate
                tokens = math.min(capacity, tokens + refill)

                if tokens >= 1 then
                    tokens = tokens - 1
                    redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)
                    redis.call('EXPIRE', key, 3600)
                    return 1
                else
                    redis.call('HSET', key, 'tokens', tokens, 'last_refill', now)
                    redis.call('EXPIRE', key, 3600)
                    return 0
                end
                """);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit annotation = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (annotation == null) {
            return true;
        }

        String key = RedisConstant.RATE_LIMIT_KEY_PREFIX +
                (annotation.key().isEmpty() ? request.getRequestURI() : annotation.key());
        long capacity = annotation.capacity();
        double refillRate = annotation.refillRate();
        long now = System.currentTimeMillis();

        Long result = stringRedisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(now)
        );

        if (result != null && result == 1L) {
            return true;
        }
        throw new ClientException("请求过于频繁，请稍后重试");
    }
}
