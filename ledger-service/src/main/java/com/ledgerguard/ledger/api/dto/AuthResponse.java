package com.ledgerguard.ledger.api.dto;

public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
