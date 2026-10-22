package com.ledgerguard.ledger.service;

import com.ledgerguard.ledger.domain.TransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResult(
        UUID transferId,
        TransferStatus status,
        String rejectReason,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        Instant createdAt,
        boolean replayed
) {
}
