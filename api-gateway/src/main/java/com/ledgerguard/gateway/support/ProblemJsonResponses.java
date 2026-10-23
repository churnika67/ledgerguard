package com.ledgerguard.gateway.support;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/** Writes an RFC 7807 problem+json body, matching the shape ledger-service's own error handler
 * uses, for the two cases the gateway itself short-circuits: a rejected/missing JWT (401) and a
 * rate-limited request (429). */
public final class ProblemJsonResponses {

    private ProblemJsonResponses() {
    }

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String detail) {
        return write(exchange.getResponse(), status, detail);
    }

    public static Mono<Void> write(ServerHttpResponse response, HttpStatus status, String detail) {
        response.setStatusCode(status);
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String json = """
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), escape(detail));
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static String escape(String value) {
        return value.replace("\"", "'");
    }
}
