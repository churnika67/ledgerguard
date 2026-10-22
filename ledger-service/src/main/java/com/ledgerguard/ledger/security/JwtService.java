package com.ledgerguard.ledger.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/** Issues and validates the demo JWTs used by every endpoint except {@code /auth/token}. */
@Component
public class JwtService {

    private static final String ROLES_CLAIM = "roles";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(@Value("${jwt.secret}") String secret,
                       @Value("${jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String issue(String username, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(ROLES_CLAIM, roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public Duration expiresIn() {
        return expiration;
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
