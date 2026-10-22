package com.ledgerguard.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A money account. Balance is the single source of truth for "how much is here right now";
 * it must always equal openingBalance + sum(credits) - sum(debits) in the ledger_entries table,
 * and is enforced to never go negative at the service layer.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Account() {
        // JPA
    }

    public Account(UUID id, String ownerName, String currency, BigDecimal openingBalance, Instant createdAt) {
        this.id = id;
        this.ownerName = ownerName;
        this.currency = currency;
        this.balance = openingBalance;
        this.openingBalance = openingBalance;
        this.version = 0L;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    /** @throws InsufficientFundsException if the debit would push the balance below zero */
    public void debit(BigDecimal amount) {
        BigDecimal result = this.balance.subtract(amount);
        if (result.signum() < 0) {
            throw new InsufficientFundsException(this.id);
        }
        this.balance = result;
    }

    public static final class InsufficientFundsException extends RuntimeException {
        private final UUID accountId;

        public InsufficientFundsException(UUID accountId) {
            super("Account " + accountId + " has insufficient funds");
            this.accountId = accountId;
        }

        public UUID getAccountId() {
            return accountId;
        }
    }
}
