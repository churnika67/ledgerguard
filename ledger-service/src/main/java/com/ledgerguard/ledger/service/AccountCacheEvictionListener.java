package com.ledgerguard.ledger.service;

import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Evicts the cached {@code GET /accounts/{id}} response for both accounts in a transfer, but
 * only once the transaction that moved the money has actually committed. Doing this inside the
 * business transaction instead would be a race: a concurrent reader could repopulate the cache
 * with the pre-commit balance in the gap between eviction and commit, leaving a stale entry
 * sitting in Redis indefinitely (or until the 60s safety-net TTL expires).
 */
@Component
class AccountCacheEvictionListener {

    private static final String CACHE_NAME = "accounts";

    private final CacheManager cacheManager;

    AccountCacheEvictionListener(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onAccountsMutated(AccountsMutatedEvent event) {
        var cache = cacheManager.getCache(CACHE_NAME);
        if (cache == null) {
            return;
        }
        cache.evict(event.fromAccountId());
        cache.evict(event.toAccountId());
    }
}
