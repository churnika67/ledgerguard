package com.ledgerguard.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The durable record of one transfer request. {@code idempotencyKey} plus {@code requestHash}
 * is what makes retries safe: a replay with the same key and the same body returns this row
 * unchanged, while the same key with a different body is a client error (409).
 */
@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Transfer() {
        // JPA
    }

    private Transfer(UUID id, String idempotencyKey, String requestHash, UUID fromAccountId, UUID toAccountId,
                      BigDecimal amount, String currency, TransferStatus status, String rejectReason,
                      Instant createdAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.rejectReason = rejectReason;
        this.createdAt = createdAt;
    }

    public static Transfer completed(UUID id, String idempotencyKey, String requestHash, UUID fromAccountId,
                                      UUID toAccountId, BigDecimal amount, String currency, Instant createdAt) {
        return new Transfer(id, idempotencyKey, requestHash, fromAccountId, toAccountId, amount, currency,
                TransferStatus.COMPLETED, null, createdAt);
    }

    public static Transfer rejected(UUID id, String idempotencyKey, String requestHash, UUID fromAccountId,
                                     UUID toAccountId, BigDecimal amount, String currency, String reason,
                                     Instant createdAt) {
        return new Transfer(id, idempotencyKey, requestHash, fromAccountId, toAccountId, amount, currency,
                TransferStatus.REJECTED, reason, createdAt);
    }

    public UUID getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
