package com.ledgerguard.ledger.api;

import com.ledgerguard.ledger.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TransferApiIntegrationTest extends PostgresIntegrationTest {

    private String aliceToken() {
        return token("alice", "password123");
    }

    private String openAccount(String token, String ownerName, String currency, String openingBalance) {
        HttpHeaders headers = authHeaders(token);
        ResponseEntity<Map> response = restTemplate.exchange(baseUrl() + "/accounts", HttpMethod.POST,
                new HttpEntity<>(Map.of("ownerName", ownerName, "currency", currency,
                        "openingBalance", openingBalance), headers),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    private ResponseEntity<Map> transfer(String token, String idempotencyKey, String from, String to,
                                          String amount, String currency) {
        HttpHeaders headers = authHeaders(token);
        headers.add("Idempotency-Key", idempotencyKey);
        return restTemplate.exchange(baseUrl() + "/transfers", HttpMethod.POST,
                new HttpEntity<>(Map.of("fromAccountId", from, "toAccountId", to, "amount", amount,
                        "currency", currency), headers),
                Map.class);
    }

    @Test
    void replayingSameKeyAndBodyReturnsOriginalResultWithoutMovingMoneyAgain() {
        String token = aliceToken();
        String from = openAccount(token, "alice", "USD", "100.00");
        String to = openAccount(token, "alice", "USD", "0.00");
        String key = "key-" + UUID.randomUUID();

        ResponseEntity<Map> first = transfer(token, key, from, to, "30.00", "USD");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String transferId = (String) first.getBody().get("transferId");

        ResponseEntity<Map> replay = transfer(token, key, from, to, "30.00", "USD");
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(replay.getBody().get("transferId")).isEqualTo(transferId);

        Map<?, ?> account = restTemplate.exchange(baseUrl() + "/accounts/" + to, HttpMethod.GET,
                new HttpEntity<>(authHeaders(token)), Map.class).getBody();
        assertThat(account.get("balance")).isEqualTo(30.00);
    }

    @Test
    void sameKeyWithDifferentBodyIsConflict() {
        String token = aliceToken();
        String from = openAccount(token, "alice", "USD", "100.00");
        String to = openAccount(token, "alice", "USD", "0.00");
        String key = "key-" + UUID.randomUUID();

        transfer(token, key, from, to, "30.00", "USD");
        ResponseEntity<Map> conflict = transfer(token, key, from, to, "31.00", "USD");

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void insufficientFundsIsRejectedWithReason() {
        String token = aliceToken();
        String from = openAccount(token, "alice", "USD", "10.00");
        String to = openAccount(token, "alice", "USD", "0.00");

        ResponseEntity<Map> response = transfer(token, "key-" + UUID.randomUUID(), from, to, "30.00", "USD");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("status")).isEqualTo("REJECTED");
        assertThat(response.getBody().get("rejectReason")).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void sameAccountTransferIsRejected() {
        String token = aliceToken();
        String account = openAccount(token, "alice", "USD", "100.00");

        ResponseEntity<Map> response = transfer(token, "key-" + UUID.randomUUID(), account, account, "10.00", "USD");

        assertThat(response.getBody().get("status")).isEqualTo("REJECTED");
        assertThat(response.getBody().get("rejectReason")).isEqualTo("SAME_ACCOUNT");
    }

    @Test
    void currencyMismatchIsRejected() {
        String token = aliceToken();
        String usdAccount = openAccount(token, "alice", "USD", "100.00");
        String eurAccount = openAccount(token, "alice", "EUR", "0.00");

        ResponseEntity<Map> response = transfer(token, "key-" + UUID.randomUUID(), usdAccount, eurAccount,
                "10.00", "USD");

        assertThat(response.getBody().get("status")).isEqualTo("REJECTED");
        assertThat(response.getBody().get("rejectReason")).isEqualTo("CURRENCY_MISMATCH");
    }

    @Test
    void missingIdempotencyKeyHeaderIsBadRequest() {
        String token = aliceToken();
        String from = openAccount(token, "alice", "USD", "100.00");
        String to = openAccount(token, "alice", "USD", "0.00");

        ResponseEntity<Map> response = restTemplate.exchange(baseUrl() + "/transfers", HttpMethod.POST,
                new HttpEntity<>(Map.of("fromAccountId", from, "toAccountId", to, "amount", "10.00",
                        "currency", "USD"), authHeaders(token)),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
