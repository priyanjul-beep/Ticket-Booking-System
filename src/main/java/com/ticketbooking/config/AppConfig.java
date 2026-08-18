package com.ticketbooking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${app.booking.expiry-minutes:5}")
    private int bookingExpiryMinutes;

    @Value("${app.lock.wait-seconds:5}")
    private long lockWaitSeconds;

    @Value("${app.lock.lease-seconds:10}")
    private long lockLeaseSeconds;

    @Value("${app.redis.lock-prefix:lock:seat:}")
    private String redisLockPrefix;

    @Value("${app.redis.idempotency-ttl-seconds:86400}")
    private long idempotencyTtlSeconds;

    public int getBookingExpiryMinutes()      { return bookingExpiryMinutes; }
    public long getLockWaitSeconds()          { return lockWaitSeconds; }
    public long getLockLeaseSeconds()         { return lockLeaseSeconds; }
    public String getRedisLockPrefix()        { return redisLockPrefix; }
    public long getIdempotencyTtlSeconds()    { return idempotencyTtlSeconds; }
}
