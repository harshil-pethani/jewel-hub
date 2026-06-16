package com.hpethani.commonsecurity.entrypoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ServiceAuthEntryPoint — handles unauthorized access to downstream services
 * when someone bypasses the API Gateway and hits the service directly on its port.
 *
 * Returns a consistent JSON 401 response instead of Spring's default HTML error page.
 *
 * This is only triggered when:
 *   1. A request reaches the service directly (not through the gateway)
 *   2. The "email" header is missing (gateway sets it after JWT validation)
 *   3. Spring Security blocks the unauthenticated request
 */
public class ServiceAuthEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ServiceAuthEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("error", HttpStatus.UNAUTHORIZED.getReasonPhrase());
        body.put("message", "Direct service access is not allowed. Route through the API Gateway.");
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("path", request.getRequestURI());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}

