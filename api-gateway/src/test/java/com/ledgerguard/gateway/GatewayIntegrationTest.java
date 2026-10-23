package com.ledgerguard.gateway;

import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real gateway (real routing, real JWT filter, real Redis-backed rate limiter)
 * against a trivial stub "ledger-service" so no other module needs to be running. Proves: a
 * request with no token is rejected before it ever reaches the backend; a valid token is let
 * through and the response is proxied back; and enough rapid requests from the same user trip
 * the per-user rate limit with a problem+json 429.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayIntegrationTest {

    private static final String SECRET = "ledgerguard-demo-secret-key-change-me-please-32bytes-min";

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static final HttpServer STUB_BACKEND = startStub();

    static {
        REDIS.start();
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = "{\"ok\":true}".getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        int stubPort = STUB_BACKEND.getAddress().getPort();
        registry.add("LEDGER_SERVICE_URL", () -> "http://localhost:" + stubPort);
        registry.add("NOTIFICATION_SERVICE_URL", () -> "http://localhost:" + stubPort);
    }

    @LocalServerPort
    private int port;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void requestWithNoTokenIsRejected() {
        client.get().uri("/api/ledger/accounts/00000000-0000-0000-0000-000000000000")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectHeader().contentType("application/problem+json");
    }

    @Test
    void requestWithAValidTokenIsProxiedThrough() {
        String token = validToken("alice");

        client.get().uri("/api/ledger/accounts/00000000-0000-0000-0000-000000000000")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.ok").isEqualTo(true);
    }

    @Test
    void authTokenPathIsReachableWithoutAToken() {
        client.post().uri("/api/ledger/auth/token")
                .bodyValue("{\"username\":\"alice\",\"password\":\"password123\"}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void burstOfRequestsFromTheSameUserEventuallyTripsTheRateLimit() {
        String token = validToken("rate-limit-test-user");
        boolean sawTooManyRequests = false;

        for (int i = 0; i < 40 && !sawTooManyRequests; i++) {
            HttpStatusCode status = client.get()
                    .uri("/api/ledger/accounts/00000000-0000-0000-0000-000000000000")
                    .header("Authorization", "Bearer " + token)
                    .exchange()
                    .returnResult(String.class)
                    .getStatus();
            if (status.equals(HttpStatus.TOO_MANY_REQUESTS)) {
                sawTooManyRequests = true;
            }
        }

        assertThat(sawTooManyRequests)
                .as("a tight burst of requests from one user should eventually hit the rate limit")
                .isTrue();
    }

    private String validToken(String username) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        return Jwts.builder()
                .subject(username)
                .claim("roles", List.of("ROLE_USER"))
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}
