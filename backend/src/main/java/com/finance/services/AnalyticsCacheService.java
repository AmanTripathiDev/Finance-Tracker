package com.finance.services;

import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalyticsCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void evictUserAnalytics(UUID userId) {
        evictByPattern("reports::*:" + userId + "*");
        evictByPattern("dashboard::*:" + userId + "*");
    }

    private void evictByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
