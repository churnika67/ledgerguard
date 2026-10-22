package com.ledgerguard.ledger.api.dto;

import com.ledgerguard.ledger.domain.Transfer;
import com.ledgerguard.ledger.domain.TransferStatus;
import com.ledgerguard.ledger.service.TransferResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponseDto(UUID transferId, TransferStatus status, String rejectReason,
                                   UUID fromAccountId, UUID toAccountId, BigDecimal amount, String currency,
                                   Instant createdAt) {

    public static TransferResponseDto from(TransferResult result) {
        return new TransferResponseDto(result.transferId(), result.status(), result.rejectReason(),
                result.fromAccountId(), result.toAccountId(), result.amount(), result.currency(),
                result.createdAt());
    }

    public static TransferResponseDto from(Transfer transfer) {
        return new TransferResponseDto(transfer.getId(), transfer.getStatus(), transfer.getRejectReason(),
                transfer.getFromAccountId(), transfer.getToAccountId(), transfer.getAmount(),
                transfer.getCurrency(), transfer.getCreatedAt());
    }
}
