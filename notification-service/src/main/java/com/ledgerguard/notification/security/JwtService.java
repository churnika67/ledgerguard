package com.ledgerguard.notification.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Verifies the JWTs issued by ledger-service's {@code /auth/token}. This service never issues
 * tokens itself, so it only needs the shared signing secret to validate them.
 */
@Component
public class JwtService {

    private static final String ROLES_CLAIM = "roles";

    private final SecretKey key;

    public JwtService(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or has a bad signature */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @SuppressWarnings("unchecked")
    public List<String> rolesOf(Claims claims) {
        return (List<String>) claims.get(ROLES_CLAIM, List.class);
    }
}
