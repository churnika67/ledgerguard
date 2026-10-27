package com.ledgerguard.ledger.graphql;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferInput(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String currency,
                             String idempotencyKey) {
}
