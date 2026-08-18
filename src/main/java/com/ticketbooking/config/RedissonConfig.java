package com.ticketbooking.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson configuration for distributed locking.
 *
 * <p>WHY REDIS DISTRIBUTED LOCK?
 * When the application runs as multiple instances (horizontal scaling),
 * a JVM-level ReentrantLock is no longer sufficient — each instance has
 * its own lock map in memory, so two concurrent requests on different
 * nodes can both acquire the "same" lock simultaneously, leading to
 * double bookings. Redis acts as a shared external lock store visible
 * to all instances.
 *
 * <p>Redisson's RLock implements the Redlock algorithm, providing:
 * - Atomic lock acquisition (SET NX PX in a single command)
 * - TTL-based automatic expiry (prevents lock leaks on crash)
 * - Watchdog for lease extension during long operations
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = String.format("redis://%s:%d", redisHost, redisPort);

        config.useSingleServer()
              .setAddress(address)
              .setPassword(redisPassword.isBlank() ? null : redisPassword)
              .setConnectionMinimumIdleSize(5)
              .setConnectionPoolSize(20)
              .setRetryAttempts(3)
              .setRetryInterval(1500)
              .setTimeout(3000)
              .setConnectTimeout(3000);

        return Redisson.create(config);
    }
}
