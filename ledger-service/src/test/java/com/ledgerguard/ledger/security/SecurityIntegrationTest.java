package com.ledgerguard.ledger.security;

import com.ledgerguard.ledger.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityIntegrationTest extends PostgresIntegrationTest {

    @Test
    void requestWithoutTokenIsUnauthorized() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl() + "/accounts/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType().toString()).contains("problem+json");
    }

    @Test
    void userRoleCallingReconciliationIsForbidden() {
        String userToken = token("alice", "password123");

        ResponseEntity<String> response = restTemplate.exchange(baseUrl() + "/reconciliation",
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType().toString()).contains("problem+json");
    }

    @Test
    void adminRoleCanReadReconciliation() {
        String adminToken = token("admin", "admin123");

        ResponseEntity<String> response = restTemplate.exchange(baseUrl() + "/reconciliation",
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(authHeaders(adminToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void wrongCredentialsAreRejectedWithUnauthorized() {
        ResponseEntity<String> response = restTemplate.postForEntity(baseUrl() + "/auth/token",
                Map.of("username", "alice", "password", "wrong-password"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userCannotReadAnotherUsersAccount() {
        String aliceToken = token("alice", "password123");
        String bobToken = token("bob", "password123");

        ResponseEntity<Map> created = restTemplate.exchange(baseUrl() + "/accounts",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(Map.of("ownerName", "bob", "currency", "USD", "openingBalance", "100.00"),
                        authHeaders(bobToken)),
                Map.class);
        String bobAccountId = (String) created.getBody().get("id");

        ResponseEntity<String> response = restTemplate.exchange(baseUrl() + "/accounts/" + bobAccountId,
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(authHeaders(aliceToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
