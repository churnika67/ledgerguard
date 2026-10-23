package com.ledgerguard.gateway.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "ledgerguard-demo-secret-key-change-me-please-32bytes-min";

    private final JwtService jwtService = new JwtService(SECRET);

    @Test
    void parsesAValidTokenSignedWithTheSameSecret() {
        String token = tokenSignedWith(SECRET, Instant.now().plusSeconds(3600));

        var claims = jwtService.parse(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
    }

    @Test
    void rejectsAnExpiredToken() {
        String token = tokenSignedWith(SECRET, Instant.now().minusSeconds(10));

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        String token = tokenSignedWith("a-completely-different-secret-key-that-is-long-enough",
                Instant.now().plusSeconds(3600));

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(JwtException.class);
    }

    private String tokenSignedWith(String secret, Instant expiry) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .subject("alice")
                .claim("roles", java.util.List.of("ROLE_USER"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }
}
