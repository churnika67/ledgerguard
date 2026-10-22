package com.ledgerguard.ledger.api.dto;

import com.ledgerguard.ledger.domain.Account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(UUID id, String ownerName, String currency, BigDecimal balance, Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getOwnerName(), account.getCurrency(),
                account.getBalance(), account.getCreatedAt());
    }
}
