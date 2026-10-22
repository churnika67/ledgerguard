package com.ledgerguard.ledger.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Returns problem+json instead of a WWW-Authenticate challenge page when a request is unauthenticated. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        ProblemDetailSupport.write(response, HttpStatus.UNAUTHORIZED,
                "Authentication is required to access this resource");
    }
}
