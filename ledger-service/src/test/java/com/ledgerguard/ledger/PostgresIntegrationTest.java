package com.ledgerguard.ledger;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for tests that need a real Postgres but no Kafka. The container is started once
 * as a JVM-wide singleton (not per test class) and left running; Testcontainers' Ryuk sidecar
 * reaps it when the JVM exits. This avoids restart/port races when several test classes share
 * the same container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ledger.kafka.enabled=false", "ledger.cache.enabled=false"})
public abstract class PostgresIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledgerguard")
            .withUsername("ledgerguard")
            .withPassword("ledgerguard");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    protected int port;

    // The JDK HttpClient-based factory handles a 401 response on a POST correctly; the legacy
    // HttpURLConnection-based factory throws HttpRetryException trying to replay the streamed body.
    protected TestRestTemplate restTemplate = new TestRestTemplate(
            new RestTemplateBuilder().requestFactory(() -> new JdkClientHttpRequestFactory()));

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected String token(String username, String password) {
        var request = java.util.Map.of("username", username, "password", password);
        var response = restTemplate.postForEntity(baseUrl() + "/auth/token", request,
                com.ledgerguard.ledger.api.dto.AuthResponse.class);
        return response.getBody().accessToken();
    }

    protected org.springframework.http.HttpHeaders authHeaders(String token) {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
