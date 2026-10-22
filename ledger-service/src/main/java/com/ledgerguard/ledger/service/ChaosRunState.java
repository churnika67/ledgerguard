package com.ledgerguard.ledger.service;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable snapshot of one chaos run's progress. The registry swaps the value stored under a
 * run's id as it moves RUNNING -> COMPLETED/FAILED, rather than mutating a shared object, so a
 * concurrent GET never observes a half-written result.
 */
public record ChaosRunState(
        UUID id,
        ChaosRunStatus status,
        String strategy,
        int requestedTransferCount,
        int accountCount,
        int concurrency,
        Instant startedAt,
        Instant finishedAt,
        Double transfersPerSecond,
        Long p95LatencyMillis,
        Long retries,
        Long conflicts,
        Long failures,
        Boolean invariantsHealthy,
        java.math.BigDecimal totalBalance,
        java.math.BigDecimal totalOpeningBalance,
        Integer accountMismatchCount,
        Integer transferMismatchCount,
        String errorMessage
) {

    public enum ChaosRunStatus {
        RUNNING, COMPLETED, FAILED
    }

    static ChaosRunState starting(UUID id, String strategy, int transferCount, int accountCount, int concurrency) {
        return new ChaosRunState(id, ChaosRunStatus.RUNNING, strategy, transferCount, accountCount, concurrency,
                Instant.now(), null, null, null, null, null, null, null, null, null, null, null, null);
    }

    ChaosRunState completed(double transfersPerSecond, long p95LatencyMillis, long retries, long conflicts,
                             long failures, ReconciliationReport report) {
        return new ChaosRunState(id, ChaosRunStatus.COMPLETED, strategy, requestedTransferCount, accountCount,
                concurrency, startedAt, Instant.now(), transfersPerSecond, p95LatencyMillis, retries, conflicts,
                failures, report.healthy(), report.totalBalance(), report.totalOpeningBalance(),
                report.accountsWithMismatchedBalance().size(), report.transfersNotNettingToZero().size(), null);
    }

    ChaosRunState failed(String errorMessage) {
        return new ChaosRunState(id, ChaosRunStatus.FAILED, strategy, requestedTransferCount, accountCount,
                concurrency, startedAt, Instant.now(), null, null, null, null, null, null, null, null, null, null,
                errorMessage);
    }
}
