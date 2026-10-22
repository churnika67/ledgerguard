package com.ledgerguard.ledger.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Two mutually exclusive CacheManagers, chosen by {@code ledger.cache.enabled} (same on/off
 * pattern as {@code ledger.kafka.enabled}). The Redis-backed one is what runs in production;
 * tests default it off so they never need a live Redis, the same way Kafka defaults off there.
 * A short TTL (60s) is kept as a safety net on top of the explicit post-commit eviction below --
 * belt and braces against any mutation path that might ever bypass the eviction listener.
 */
@Configuration
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(prefix = "ledger.cache", name = "enabled", matchIfMissing = true)
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                                           @Value("${ledger.cache.accounts-ttl-seconds:60}") long accountsTtlSeconds) {
        // This ObjectMapper only ever (de)serializes our own domain objects round-tripping
        // through our own private Redis instance, never untrusted input, so a scoped allowlist
        // of our package plus the handful of JDK value types our entities use is safe -- the
        // point of a PolymorphicTypeValidator is blocking gadget-chain attacks from external
        // JSON, which doesn't apply here.
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.ledgerguard.ledger.domain")
                .allowIfSubType("java.math.BigDecimal")
                .allowIfSubType("java.time.Instant")
                .allowIfSubType("java.util")
                .build();
        ObjectMapper redisObjectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(accountsTtlSeconds))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new GenericJackson2JsonRedisSerializer(redisObjectMapper)));
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "ledger.cache", name = "enabled", havingValue = "false")
    public CacheManager noOpCacheManager() {
        // Resolves a Cache for any name but never actually stores anything -- @Cacheable
        // methods just always run, so tests and local runs without Redis configured don't
        // need one.
        return new NoOpCacheManager();
    }
}
