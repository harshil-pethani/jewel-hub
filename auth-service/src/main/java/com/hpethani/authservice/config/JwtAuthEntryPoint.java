package com.hpethani.authservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpethani.commonconfig.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Handles unauthenticated access attempts to protected endpoints.
 *
 * Triggered by Spring Security's ExceptionTranslationFilter when:
 *  - No JWT in request (SecurityContext is empty)
 *  - Request hits /api/user/** directly on this port (bypassing gateway)
 *
 * Returns consistent JSON 401 — same ErrorResponse structure as the rest of the API.
 * Without this, Spring Security redirects to a default HTML login page.
 */
@Component
public class JwtAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = new ErrorResponse(
                "Unauthorized: " + authException.getMessage(),
                HttpStatus.UNAUTHORIZED
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
