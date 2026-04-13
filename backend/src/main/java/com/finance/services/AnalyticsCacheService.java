package com.finance.services;

import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void evictUserAnalytics(UUID userId) {
        try {
            evictByPattern("reports::*:" + userId + "*");
            evictByPattern("dashboard::*:" + userId + "*");
        } catch (RuntimeException exception) {
            log.warn("Skipping analytics cache eviction for user {} because Redis is unavailable or misconfigured", userId, exception);
        }
    }

    private void evictByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
