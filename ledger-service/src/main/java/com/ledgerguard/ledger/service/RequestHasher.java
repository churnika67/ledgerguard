package com.ledgerguard.ledger.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Hashes the semantically-relevant fields of a transfer request for idempotency-key reuse checks. */
final class RequestHasher {

    private RequestHasher() {
    }

    static String hash(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String currency) {
        String canonical = fromAccountId + "|" + toAccountId + "|" + amount.stripTrailingZeros().toPlainString()
                + "|" + currency;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
