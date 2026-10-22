package com.ledgerguard.ledger.service;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String currency) {
}
