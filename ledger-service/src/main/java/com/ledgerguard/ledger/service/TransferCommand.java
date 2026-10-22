package com.ledgerguard.ledger.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A validated request to move money, already assigned a transfer id and a request hash. */
public record TransferCommand(
        UUID transferId,
        String idempotencyKey,
        String requestHash,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        String currency,
        Instant createdAt
) {
}
