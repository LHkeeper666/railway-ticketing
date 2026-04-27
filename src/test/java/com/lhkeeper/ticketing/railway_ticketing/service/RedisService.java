package com.lhkeeper.ticketing.railway_ticketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value); // 设置值
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key); // 获取值
    }

    public void delete(String key) {
        redisTemplate.delete(key); // 删除值
    }
}
