package com.ledgerguard.gateway.security;

import com.ledgerguard.gateway.support.ProblemJsonResponses;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Set;

/**
 * Validates every request's bearer token before it reaches ledger-service or
 * notification-service, except the handful of routes that have to stay open (issuing a token in
 * the first place, and the gateway's own health check). On success, the JWT subject is stashed
 * as an exchange attribute so {@link UserKeyResolver} can rate-limit per authenticated user
 * rather than per IP.
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String USERNAME_ATTRIBUTE = "ledgerguard.username";

    private static final Set<String> PUBLIC_PATHS = Set.of("/api/ledger/auth/token");

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS
                || PUBLIC_PATHS.contains(path) || path.startsWith("/actuator")) {
            // CORS preflight is normally fully handled by Spring Cloud Gateway's own
            // CorsWebFilter before routing ever reaches this filter; this is a defensive
            // fallback so a preflight request is never the thing that breaks CORS with a 401.
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ProblemJsonResponses.write(exchange, HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }

        try {
            Claims claims = jwtService.parse(authHeader.substring(7));
            exchange.getAttributes().put(USERNAME_ATTRIBUTE, claims.getSubject());
            return chain.filter(exchange);
        } catch (JwtException e) {
            return ProblemJsonResponses.write(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    @Override
    public int getOrder() {
        // Must run before the RequestRateLimiter filter so the username attribute it reads is
        // already set; NettyRoutingFilter (which actually forwards the request) runs much later.
        return -1;
    }
}
