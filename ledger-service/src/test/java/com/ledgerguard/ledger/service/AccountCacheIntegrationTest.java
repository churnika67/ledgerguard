package com.ledgerguard.ledger.service;

import com.ledgerguard.ledger.PostgresIntegrationTest;
import com.ledgerguard.ledger.domain.Account;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Redis-backed accounts cache does what it's for: a second read is served from the
 * cache, and -- the one that actually matters -- a transfer that changes the balance evicts the
 * cache entry before any reader can observe the old number again. Runs with its own Redis
 * container and overrides {@code ledger.cache.enabled} back to true via a dynamic property,
 * since the shared {@link PostgresIntegrationTest} base turns caching off for every other test.
 */
class AccountCacheIntegrationTest extends PostgresIntegrationTest {

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("ledger.cache.enabled", () -> "true");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private AccountService accountService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private CacheManager cacheManager;

    @Test
    void secondReadIsServedFromCache() {
        Account account = accountService.open("cache-owner-1", "USD", new BigDecimal("100.00"));

        Account first = accountService.get(account.getId());
        Cache cache = cacheManager.getCache("accounts");
        assertThat(cache).isNotNull();
        assertThat(cache.get(account.getId())).isNotNull();

        Account second = accountService.get(account.getId());
        assertThat(second.getBalance()).isEqualByComparingTo(first.getBalance());
    }

    @Test
    void transferEvictsTheCacheSoNoStaleBalanceIsEverServedAfterwards() {
        Account from = accountService.open("cache-owner-from", "USD", new BigDecimal("500.00"));
        Account to = accountService.open("cache-owner-to", "USD", new BigDecimal("0.00"));

        // Warm the cache for both accounts with their pre-transfer balances.
        accountService.get(from.getId());
        accountService.get(to.getId());
        Cache cache = cacheManager.getCache("accounts");
        assertThat(cache).isNotNull();
        assertThat(cache.get(from.getId())).isNotNull();
        assertThat(cache.get(to.getId())).isNotNull();

        TransferResult result = transferService.submit(
                new TransferRequest(from.getId(), to.getId(), new BigDecimal("150.00"), "USD"),
                "cache-test-" + UUID.randomUUID());
        assertThat(result.status().name()).isEqualTo("COMPLETED");

        // The post-commit listener must have evicted both entries by the time submit() returns.
        assertThat(cache.get(from.getId())).isNull();
        assertThat(cache.get(to.getId())).isNull();

        // And the next read -- which repopulates the cache -- must show the new balance, not
        // the one that was cached before the transfer.
        Account freshFrom = accountService.get(from.getId());
        Account freshTo = accountService.get(to.getId());
        assertThat(freshFrom.getBalance()).isEqualByComparingTo("350.00");
        assertThat(freshTo.getBalance()).isEqualByComparingTo("150.00");
    }
}
