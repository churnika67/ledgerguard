package com.ledgerguard.ledger.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Returns problem+json when an authenticated principal lacks the role or ownership to proceed. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        ProblemDetailSupport.write(response, HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action");
    }
}
