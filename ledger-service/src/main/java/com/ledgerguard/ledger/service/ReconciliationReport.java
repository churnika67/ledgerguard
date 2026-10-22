package com.ledgerguard.ledger.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Result of checking the three invariants LedgerGuard must never violate:
 * (a) the sum of all balances equals the sum of all opening balances,
 * (b) every account's balance equals openingBalance + credits - debits, and
 * (c) every completed transfer's ledger entries net to zero.
 */
public record ReconciliationReport(
        boolean healthy,
        BigDecimal totalBalance,
        BigDecimal totalOpeningBalance,
        List<UUID> accountsWithMismatchedBalance,
        List<UUID> transfersNotNettingToZero
) {
}
