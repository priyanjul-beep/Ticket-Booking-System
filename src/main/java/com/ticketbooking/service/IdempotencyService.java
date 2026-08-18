package com.ticketbooking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.config.AppConfig;
import com.ticketbooking.entity.IdempotencyRecord;
import com.ticketbooking.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

/**
 * Idempotency Service using Redis (Fast Cache) + PostgreSQL (Durable Store).
 *
 * Prevents double booking when a client accidentally retries a request
 * with the exact same Idempotency-Key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppConfig appConfig;

    private static final String REDIS_PREFIX = "idempotency:";

    public Optional<IdempotencyRecord> getRecord(String idempotencyKey) {
        String redisKey = REDIS_PREFIX + idempotencyKey;
        try {
            String cachedJson = redisTemplate.opsForValue().get(redisKey);
            if (cachedJson != null) {
                IdempotencyRecord record = objectMapper.readValue(cachedJson, IdempotencyRecord.class);
                log.info("IDEMPOTENCY_CACHE_HIT in Redis for key: {}", idempotencyKey);
                return Optional.of(record);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for idempotency lookup, falling back to DB: {}", e.getMessage());
        }

        // DB fallback
        Optional<IdempotencyRecord> dbRecord = idempotencyRepository.findByIdempotencyKey(idempotencyKey);
        dbRecord.ifPresent(record -> cacheInRedis(idempotencyKey, record));
        return dbRecord;
    }

    @Transactional
    public IdempotencyRecord saveRecord(String idempotencyKey, Object responseObj, int statusCode) {
        try {
            String jsonStr = objectMapper.writeValueAsString(responseObj);
            IdempotencyRecord record = IdempotencyRecord.builder()
                    .idempotencyKey(idempotencyKey)
                    .responseBody(jsonStr)
                    .statusCode(statusCode)
                    .build();

            IdempotencyRecord saved = idempotencyRepository.save(record);
            cacheInRedis(idempotencyKey, saved);
            log.info("IDEMPOTENCY_RECORD_SAVED for key: {}", idempotencyKey);
            return saved;
        } catch (Exception e) {
            log.error("Failed to save idempotency record for key: {}", idempotencyKey, e);
            throw new RuntimeException("Idempotency save failed", e);
        }
    }

    private void cacheInRedis(String idempotencyKey, IdempotencyRecord record) {
        try {
            String jsonStr = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(
                    REDIS_PREFIX + idempotencyKey,
                    jsonStr,
                    Duration.ofSeconds(appConfig.getIdempotencyTtlSeconds())
            );
        } catch (Exception e) {
            log.warn("Failed to cache idempotency record in Redis", e);
        }
    }
}
