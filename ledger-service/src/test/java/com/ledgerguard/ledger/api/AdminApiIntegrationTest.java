package com.ledgerguard.ledger.api;

import com.ledgerguard.ledger.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AdminApiIntegrationTest extends PostgresIntegrationTest {

    @Test
    void nonAdminIsForbiddenFromAdminEndpoints() {
        String userToken = token("alice", "password123");

        ResponseEntity<String> response = restTemplate.exchange(baseUrl() + "/admin/accounts",
                HttpMethod.GET, new HttpEntity<>(authHeaders(userToken)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminCanListAccountsAndTransfersWithPagination() {
        String adminToken = token("admin", "admin123");
        HttpHeaders headers = authHeaders(adminToken);

        ResponseEntity<Map> accounts = restTemplate.exchange(baseUrl() + "/admin/accounts?page=0&size=5",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(accounts.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(accounts.getBody()).containsKeys("content", "page", "size", "totalElements", "totalPages");

        ResponseEntity<Map> transfers = restTemplate.exchange(
                baseUrl() + "/admin/transfers?page=0&size=5&status=COMPLETED", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(transfers.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(transfers.getBody()).containsKeys("content", "page", "size", "totalElements", "totalPages");
    }

    @Test
    void chaosRunOverTheCapIsRejected() {
        String adminToken = token("admin", "admin123");
        HttpHeaders headers = authHeaders(adminToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("transferCount", 5001, "accountCount", 10, "concurrency", 10);
        ResponseEntity<String> response = restTemplate.exchange(baseUrl() + "/admin/chaos-runs",
                HttpMethod.POST, new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void chaosRunCompletesAndReportsHealthyInvariants() throws InterruptedException {
        String adminToken = token("admin", "admin123");
        HttpHeaders headers = authHeaders(adminToken);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of("transferCount", 40, "accountCount", 4, "concurrency", 8);
        ResponseEntity<Map> started = restTemplate.exchange(baseUrl() + "/admin/chaos-runs", HttpMethod.POST,
                new HttpEntity<>(body, headers), Map.class);
        assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String id = (String) started.getBody().get("id");
        assertThat((String) started.getBody().get("status")).isEqualTo("RUNNING");

        Map<?, ?> run = null;
        Instant deadline = Instant.now().plusSeconds(30);
        while (Instant.now().isBefore(deadline)) {
            ResponseEntity<Map> polled = restTemplate.exchange(baseUrl() + "/admin/chaos-runs/" + id,
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            run = polled.getBody();
            if (run != null && !"RUNNING".equals(run.get("status"))) {
                break;
            }
            Thread.sleep(200);
        }
        assertThat(run).isNotNull();
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(run.get("invariantsHealthy")).isEqualTo(true);
        assertThat(((Number) run.get("transfersPerSecond")).doubleValue()).isGreaterThan(0);
        assertThat(run.get("strategy")).isNotNull();
    }
}
