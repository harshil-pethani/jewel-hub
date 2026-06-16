package com.hpethani.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hpethani.apigateway.exception.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


/**
 * AuthFilter is the core security filter of the API Gateway.
 *
 * It runs BEFORE routing to any downstream service and is responsible for:
 *  1. Skipping JWT check for open/public endpoints (e.g., /auth/login, /auth/register)
 *  2. Rejecting requests with missing or invalid JWT tokens
 *  3. Extracting user identity (email, roles) from the token and forwarding
 *     them as custom headers to downstream services
 *
 * Flow:
 *   Client → API Gateway (AuthFilter) → Downstream Service
 *                  ↓
 *         Validates JWT locally (no network call)
 *                  ↓
 *         Adds X-User-Email, X-User-Roles headers
 *                  ↓
 *         Downstream service trusts these headers (internal network)
 */
@Component
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config> {

    private final JwtUtil jwtUtil;
    private final Validator validator;

    // Injected from JacksonConfig @Bean — pre-configured with JavaTimeModule
    // and WRITE_DATES_AS_TIMESTAMPS=false so LocalDateTime serializes as ISO-8601 string
    private final ObjectMapper objectMapper;

    // Shared secret stamped on every forwarded request so downstream services
    // can verify the request came from this gateway and not a direct port hit
    @Value("${gateway.internal.secret}")
    private String gatewaySecret;

    public AuthFilter(Validator validator, JwtUtil jwtUtil, ObjectMapper objectMapper) {
        super(Config.class);
        this.validator = validator;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // Step 1: Check if this route requires authentication
            // Open endpoints (e.g., /auth/**) are skipped entirely
            if (!validator.isSecured.test(request)) {
                return chain.filter(exchange);
            }

            // Step 2: Check if Authorization header is present
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return writeErrorResponse(exchange.getResponse(),
                        HttpStatus.UNAUTHORIZED, "Authorization header is missing");
            }

            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

            // Step 3: Check if header follows "Bearer <token>" format
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return writeErrorResponse(exchange.getResponse(),
                        HttpStatus.UNAUTHORIZED, "Invalid Authorization format. Use: Bearer <token>");
            }

            String token = authHeader.substring(7);

            // Step 4: Validate the JWT (signature + expiry)
            // We do NOT throw exceptions in reactive pipelines — write the response directly instead
            try {
                jwtUtil.validateToken(token);
            } catch (Exception e) {
                return writeErrorResponse(exchange.getResponse(),
                        HttpStatus.UNAUTHORIZED, "Invalid or expired token");
            }

            // Step 5: Extract user identity from token and forward to downstream services.
            // This is the INDUSTRY STANDARD approach — downstream services (order-service,
            // product-service, etc.) read these headers to know WHO is making the request,
            // without needing to validate the JWT themselves or call auth-service.
            String email = jwtUtil.extractEmail(token);
            String roles = jwtUtil.extractRoles(token);
            String userid = jwtUtil.extractUserId(token);

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("email", email)              // e.g., "john@example.com"
                    .header("roles", roles)              // e.g., "ROLE_USER" or "ROLE_ADMIN"
                    .header("userid", userid)            // e.g., "5" (user's DB id)
                    .header("X-Gateway-Secret", gatewaySecret) // proves request came from gateway
                    .build();

            // Step 6: Forward the mutated request (with identity headers) to downstream
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    /**
     * Writes a JSON error response directly to the client using ErrorResponse — the shared
     * error contract used across the entire gateway. Keeps error format consistent
     * whether the error comes from AuthFilter or GlobalExceptionHandler.
     *
     * In Spring Cloud Gateway (reactive/WebFlux), you MUST NOT throw exceptions
     * from inside a GatewayFilter lambda. Instead, write the response directly
     * and return Mono.empty() to complete the reactive chain.
     *
     * Throwing exceptions here would bypass the error response body and return
     * a generic 500 error or an unformatted response.
     */
    private Mono<Void> writeErrorResponse(ServerHttpResponse response, HttpStatus status, String message) {
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // Use ErrorResponse — same structure as GlobalExceptionHandler, consistent for clients
        ErrorResponse errorResponse = new ErrorResponse(message, status);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            // Fallback if serialization fails — should never happen in practice
            DataBuffer buffer = response.bufferFactory()
                    .wrap(("{\"status\":" + status.value() + ",\"message\":\"" + message + "\"}").getBytes());
            return response.writeWith(Mono.just(buffer));
        }
    }

    /**
     * Empty config class — required by AbstractGatewayFilterFactory.
     * Can be extended later to support per-route configuration
     * (e.g., required roles, rate limit settings).
     */
    public static class Config {}
}
