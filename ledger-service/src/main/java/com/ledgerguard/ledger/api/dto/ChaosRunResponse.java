package com.ledgerguard.ledger.api.dto;

import com.ledgerguard.ledger.service.ChaosRunState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChaosRunResponse(
        UUID id,
        ChaosRunState.ChaosRunStatus status,
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
        BigDecimal totalBalance,
        BigDecimal totalOpeningBalance,
        Integer accountMismatchCount,
        Integer transferMismatchCount,
        String errorMessage
) {

    public static ChaosRunResponse from(ChaosRunState state) {
        return new ChaosRunResponse(state.id(), state.status(), state.strategy(), state.requestedTransferCount(),
                state.accountCount(), state.concurrency(), state.startedAt(), state.finishedAt(),
                state.transfersPerSecond(), state.p95LatencyMillis(), state.retries(), state.conflicts(),
                state.failures(), state.invariantsHealthy(), state.totalBalance(), state.totalOpeningBalance(),
                state.accountMismatchCount(), state.transferMismatchCount(), state.errorMessage());
    }
}
