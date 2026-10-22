package com.ledgerguard.ledger.service;

import java.util.UUID;

/**
 * Published whenever a transfer actually changes account balances. Listened for only after the
 * owning transaction commits (see {@link AccountCacheEvictionListener}) -- evicting the cache
 * any earlier would race a concurrent reader into re-caching the pre-commit balance.
 */
record AccountsMutatedEvent(UUID fromAccountId, UUID toAccountId) {
}
