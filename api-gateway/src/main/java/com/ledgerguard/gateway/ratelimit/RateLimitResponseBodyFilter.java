package com.ledgerguard.gateway.ratelimit;

import com.ledgerguard.gateway.support.ProblemJsonResponses;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Spring Cloud Gateway's built-in RequestRateLimiter filter, when it denies a request, just sets
 * the response status to 429 and completes -- no body. This decorates the response so that if a
 * downstream filter ever calls setComplete() with no body already written and the status is 429,
 * a problem+json body gets written first, matching the shape every other rejection in this
 * system uses instead of a bare empty 429.
 */
@Component
public class RateLimitResponseBodyFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpResponse originalResponse = exchange.getResponse();
        ServerHttpResponseDecorator decorated = new ServerHttpResponseDecorator(originalResponse) {

            private boolean bodyWritten = false;

            @Override
            public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                bodyWritten = true;
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> setComplete() {
                if (!bodyWritten && getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    bodyWritten = true;
                    return ProblemJsonResponses.write(originalResponse, HttpStatus.TOO_MANY_REQUESTS,
                            "Rate limit exceeded -- slow down and try again shortly");
                }
                return super.setComplete();
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }

    @Override
    public int getOrder() {
        // Must wrap the response before the RequestRateLimiter filter runs.
        return -2;
    }
}
