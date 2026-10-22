package com.ledgerguard.ledger.api.dto;

import com.ledgerguard.ledger.domain.Direction;
import com.ledgerguard.ledger.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(UUID id, UUID transferId, Direction direction, BigDecimal amount,
                                   Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.getId(), entry.getTransferId(), entry.getDirection(),
                entry.getAmount(), entry.getCreatedAt());
    }
}
