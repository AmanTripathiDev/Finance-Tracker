package com.finance.services;

import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsCacheService {

    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;

    public void evictUserAnalytics(UUID userId) {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.debug("Skipping analytics cache eviction for user {} because Redis caching is disabled", userId);
            return;
        }
        try {
            evictByPattern("reports::*:" + userId + "*");
            evictByPattern("dashboard::*:" + userId + "*");
        } catch (RuntimeException exception) {
            log.warn("Skipping analytics cache eviction for user {} because Redis is unavailable or misconfigured", userId, exception);
        }
    }

    private void evictByPattern(String pattern) {
        RedisTemplate<String, Object> redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            return;
        }
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
