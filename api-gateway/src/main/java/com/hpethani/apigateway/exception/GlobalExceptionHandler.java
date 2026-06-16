package com.hpethani.apigateway.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * GlobalExceptionHandler handles exceptions thrown anywhere in the reactive gateway pipeline.
 *
 * WHY NOT @RestControllerAdvice here?
 * Spring Cloud Gateway is built on WebFlux (reactive). @RestControllerAdvice only
 * handles exceptions from @Controller methods — it does NOT catch exceptions thrown
 * inside GatewayFilter lambdas or at the WebFilter layer.
 *
 * The correct approach is implementing ErrorWebExceptionHandler, which is WebFlux's
 * equivalent of @ControllerAdvice but for the entire reactive pipeline.
 *
 * WHY @Order(-2) and NOT @Order(-1)?
 * Spring Boot's DefaultErrorWebExceptionHandler is ALSO @Order(-1).
 * When two beans share the same order, Spring Boot's default wins (it's registered first).
 * @Order(-2) ensures OUR handler always runs BEFORE Spring Boot's default,
 * preventing ProblemDetail responses from leaking to clients.
 *
 * Uses ErrorResponse as the single shared error contract — same structure
 * as what AuthFilter returns, so clients always get a consistent error format.
 */
@Component
@Order(-2)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    // Injected from JacksonConfig @Bean — has JavaTimeModule + WRITE_DATES_AS_TIMESTAMPS=false
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "An unexpected error occurred";

        if (ex instanceof BadRequestException badReqEx) {
            // Our own custom exception — use its status and message directly
            status = badReqEx.getStatus() != null ? badReqEx.getStatus() : HttpStatus.BAD_REQUEST;
            message = badReqEx.getMessage();

        } else if (ex instanceof ResponseStatusException responseStatusEx) {
            // Spring WebFlux exceptions: MethodNotAllowedException (405), ResponseStatusException (404, etc.)
            // These produce ProblemDetail by default — we intercept them here to return our ErrorResponse format
            HttpStatus resolved = HttpStatus.resolve(responseStatusEx.getStatusCode().value());
            status = resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
            message = responseStatusEx.getReason() != null
                    ? responseStatusEx.getReason()
                    : responseStatusEx.getMessage();
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // ErrorResponse — single consistent error shape across the entire gateway
        // Both AuthFilter and GlobalExceptionHandler return the same structure:
        // { "status": 401, "error": "Unauthorized", "message": "...", "timestamp": "..." }
        ErrorResponse errorResponse = new ErrorResponse(message, status);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }
}