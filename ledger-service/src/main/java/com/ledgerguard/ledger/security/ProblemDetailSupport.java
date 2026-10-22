package com.ledgerguard.ledger.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;

/** Writes an RFC 7807 problem+json body directly to the response, for use before MVC's own handlers run. */
final class ProblemDetailSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProblemDetailSupport() {
    }

    static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        MAPPER.writeValue(response.getWriter(), problem);
    }
}
