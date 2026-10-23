package com.ledgerguard.gateway.security;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate limits per authenticated user (the JWT subject {@link JwtAuthenticationFilter} already
 * validated and stashed), not per IP -- two people behind the same NAT/office network don't
 * share a budget, and one user can't be starved out by someone else's traffic. Falls back to the
 * caller's address only for the one route that's allowed through without a token at all
 * (issuing a token in the first place).
 */
@Configuration
public class RateLimitKeyResolverConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            Object username = exchange.getAttributes().get(JwtAuthenticationFilter.USERNAME_ATTRIBUTE);
            if (username != null) {
                return Mono.just(username.toString());
            }
            String address = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just(address);
        };
    }
}
