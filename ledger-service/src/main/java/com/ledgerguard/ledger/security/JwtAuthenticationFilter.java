package com.ledgerguard.ledger.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the {@code Authorization: Bearer <jwt>} header and populates the security context.
 *
 * <p>Re-runs on Spring MVC's async dispatch too ({@link #shouldNotFilterAsyncDispatch()}
 * overridden to {@code false}), unlike {@link OncePerRequestFilter}'s default. graphql-java's
 * engine is {@code CompletableFuture}-based even for fully synchronous resolvers, so Spring
 * GraphQL's WebMvc endpoint always completes via a {@code DeferredResult}, which means every
 * {@code /graphql} request goes through two filter-chain passes: the initial REQUEST dispatch,
 * then an ASYNC dispatch when the deferred result completes. Something upstream of this filter
 * (Spring Security's own SecurityContextHolderFilter, loading a fresh, empty context for a
 * stateless app with no session repository) resets the context to anonymous on that second pass
 * -- so without this override, every GraphQL request looked authenticated right up until the
 * access decision was actually made, which happens on the dispatch where this filter was being
 * silently skipped. Re-deriving the authentication from the header again on that second pass is
 * cheap and stateless, and sidesteps needing to reason about exactly how far Spring Security's
 * own cross-dispatch context propagation does or doesn't reach here.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                var claims = jwtService.parse(token);
                List<String> roles = jwtService.rolesOf(claims);
                var authorities = roles.stream().map(SimpleGrantedAuthority::new).toList();
                var authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException e) {
                log.debug("Rejecting invalid JWT: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
