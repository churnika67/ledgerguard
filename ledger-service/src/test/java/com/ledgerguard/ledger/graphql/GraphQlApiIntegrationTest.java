package com.ledgerguard.ledger.graphql;

import com.ledgerguard.ledger.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real GraphQL endpoint over plain HTTP (no WebTestClient/HttpGraphQlTester --
 * those need spring-webflux on the classpath, which this MVC app doesn't otherwise need), the
 * same way every other integration test in this module talks to the app: a raw POST through
 * {@code restTemplate} with a real JWT, exercising the actual Spring Security filter chain.
 */
class GraphQlApiIntegrationTest extends PostgresIntegrationTest {

    @Test
    void transferMutationAndNestedBatchedTransfersFieldBothWork() {
        String aliceToken = token("alice", "password123");
        UUID from = openAccount(aliceToken, "alice", "100.00");
        UUID to = openAccount(aliceToken, "alice", "0.00");

        String idempotencyKey = "graphql-test-" + UUID.randomUUID();
        Map<String, Object> mutationResult = graphQl(aliceToken, """
                mutation($input: TransferInput!) {
                  transfer(input: $input) { transferId status amount fromAccountId toAccountId }
                }
                """, Map.of("input", Map.of(
                "fromAccountId", from.toString(),
                "toAccountId", to.toString(),
                "amount", "30.00",
                "currency", "USD",
                "idempotencyKey", idempotencyKey)));

        assertNoErrors(mutationResult);
        Map<String, Object> transfer = asMap(data(mutationResult).get("transfer"));
        assertThat(transfer.get("status")).isEqualTo("COMPLETED");

        Map<String, Object> queryResult = graphQl(aliceToken, """
                query {
                  myAccounts {
                    id
                    balance
                    transfers { transferId amount status }
                  }
                }
                """, Map.of());

        assertNoErrors(queryResult);
        List<Map<String, Object>> accounts = asListOfMaps(data(queryResult).get("myAccounts"));
        Map<String, Object> fromAccount = accounts.stream()
                .filter(a -> a.get("id").equals(from.toString())).findFirst().orElseThrow();
        Map<String, Object> toAccount = accounts.stream()
                .filter(a -> a.get("id").equals(to.toString())).findFirst().orElseThrow();

        assertThat(new BigDecimal(fromAccount.get("balance").toString())).isEqualByComparingTo("70.00");
        assertThat(new BigDecimal(toAccount.get("balance").toString())).isEqualByComparingTo("30.00");
        assertThat(asListOfMaps(fromAccount.get("transfers"))).anyMatch(
                t -> t.get("transferId").equals(mutationResultTransferId(mutationResult)));
        assertThat(asListOfMaps(toAccount.get("transfers"))).anyMatch(
                t -> t.get("transferId").equals(mutationResultTransferId(mutationResult)));
    }

    @Test
    void accountQueryIsForbiddenForAnAccountTheCallerDoesNotOwn() {
        String aliceToken = token("alice", "password123");
        String bobToken = token("bob", "password123");
        UUID bobsAccount = openAccount(bobToken, "bob", "10.00");

        Map<String, Object> result = graphQl(aliceToken, """
                query($id: ID!) { account(id: $id) { id } }
                """, Map.of("id", bobsAccount.toString()));

        assertThat((List<?>) result.get("errors")).isNotEmpty();
    }

    @Test
    void reconciliationQueryIsAdminOnly() {
        String aliceToken = token("alice", "password123");
        Map<String, Object> deniedResult = graphQl(aliceToken, "query { reconciliation { healthy } }", Map.of());
        assertThat((List<?>) deniedResult.get("errors")).isNotEmpty();

        String adminToken = token("admin", "admin123");
        Map<String, Object> allowedResult = graphQl(adminToken, "query { reconciliation { healthy } }", Map.of());
        assertNoErrors(allowedResult);
        assertThat(asMap(data(allowedResult).get("reconciliation")).get("healthy")).isInstanceOf(Boolean.class);
    }

    @SuppressWarnings("unchecked")
    private String mutationResultTransferId(Map<String, Object> mutationResult) {
        return (String) asMap(data(mutationResult).get("transfer")).get("transferId");
    }

    private UUID openAccount(String token, String ownerName, String openingBalance) {
        var request = Map.of("ownerName", ownerName, "currency", "USD", "openingBalance", openingBalance);
        var response = restTemplate.exchange(baseUrl() + "/accounts", HttpMethod.POST,
                new HttpEntity<>(request, authHeaders(token)), Map.class);
        return UUID.fromString((String) response.getBody().get("id"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> graphQl(String token, String query, Map<String, Object> variables) {
        HttpHeaders headers = authHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = Map.of("query", query, "variables", variables);
        ResponseEntity<Map> response = restTemplate.exchange(baseUrl() + "/graphql", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        return (Map<String, Object>) response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> result) {
        return (Map<String, Object>) result.get("data");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asListOfMaps(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private void assertNoErrors(Map<String, Object> result) {
        assertThat(result.get("errors")).isNull();
    }
}
