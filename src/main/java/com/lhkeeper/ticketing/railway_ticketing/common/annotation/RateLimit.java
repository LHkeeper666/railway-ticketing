package com.lhkeeper.ticketing.railway_ticketing.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 令牌桶限流注解，标注在 Controller 方法上启用 Redis Lua 脚本限流
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流键（默认使用请求 URI） */
    String key() default "";

    /** 令牌桶容量 */
    long capacity() default 100;

    /** 令牌每秒填充速率 */
    double refillRate() default 50.0;
}
