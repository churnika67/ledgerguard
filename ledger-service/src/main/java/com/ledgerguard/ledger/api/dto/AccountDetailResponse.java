package com.ledgerguard.ledger.api.dto;

import com.ledgerguard.ledger.domain.Account;
import com.ledgerguard.ledger.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountDetailResponse(UUID id, String ownerName, String currency, BigDecimal balance,
                                     Instant createdAt, List<LedgerEntryResponse> recentEntries) {

    public static AccountDetailResponse from(Account account, List<LedgerEntry> recentEntries) {
        return new AccountDetailResponse(account.getId(), account.getOwnerName(), account.getCurrency(),
                account.getBalance(), account.getCreatedAt(), recentEntries.stream()
                .map(LedgerEntryResponse::from).toList());
    }
}
