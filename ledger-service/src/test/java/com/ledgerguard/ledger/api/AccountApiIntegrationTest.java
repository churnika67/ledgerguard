package com.ledgerguard.ledger.api;

import com.ledgerguard.ledger.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AccountApiIntegrationTest extends PostgresIntegrationTest {

    @Test
    void listMineReturnsOnlyAccountsOwnedByTheCaller() {
        String aliceToken = token("alice", "password123");
        String bobToken = token("bob", "password123");

        openAccount(aliceToken, "alice");
        openAccount(aliceToken, "alice");
        openAccount(bobToken, "bob");

        ResponseEntity<List> response = restTemplate.exchange(baseUrl() + "/accounts", HttpMethod.GET,
                new HttpEntity<>(authHeaders(aliceToken)), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map> accounts = response.getBody();
        assertThat(accounts).isNotNull();
        assertThat(accounts).allSatisfy(a -> assertThat(a.get("ownerName")).isEqualTo("alice"));
        assertThat(accounts.size()).isGreaterThanOrEqualTo(2);
    }

    private void openAccount(String token, String ownerName) {
        HttpHeaders headers = authHeaders(token);
        restTemplate.exchange(baseUrl() + "/accounts", HttpMethod.POST,
                new HttpEntity<>(Map.of("ownerName", ownerName, "currency", "USD", "openingBalance", "10.00"),
                        headers),
                Map.class);
    }
}
