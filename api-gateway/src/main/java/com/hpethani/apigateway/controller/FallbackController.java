package com.hpethani.apigateway.controller;

import com.hpethani.apigateway.exception.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * FallbackController handles Circuit Breaker fallback responses.
 *
 * When a downstream service is unavailable (timeout, crash, etc.),
 * Resilience4j circuit breaker trips and redirects here instead of
 * letting the error propagate to the client as a 500.
 *
 * Uses ErrorResponse — the single shared error contract across the entire gateway.
 * Every error (auth failure, exception, fallback) returns the same JSON structure:
 * { "status": 503, "error": "Service Unavailable", "message": "...", "timestamp": "..." }
 */
@RestController
public class FallbackController {

    /**
     * Fallback for auth-service.
     * Triggered when /auth/** routes cannot reach auth-service.
     * Configured in yml: fallbackUri: forward:/fallback/auth
     *
     * Uses @RequestMapping (not @GetMapping) — the circuit breaker forward keeps
     * the ORIGINAL HTTP method (POST, PUT, DELETE, etc.), so @GetMapping would
     * throw "Request method 'POST' is not supported" for non-GET requests.
     */
    @RequestMapping("/fallback/auth")
    public Mono<ResponseEntity<ErrorResponse>> authFallback() {
        return Mono.just(buildFallbackResponse("auth-service"));
    }

    /**
     * Fallback for product-service.
     * Triggered when /products/** routes cannot reach product-service.
     * Configured in yml: fallbackUri: forward:/fallback/product
     *
     * @RequestMapping handles GET, POST, PUT, DELETE — matches any HTTP method
     * the original client request used.
     */
    @RequestMapping("/fallback/product")
    public Mono<ResponseEntity<ErrorResponse>> productFallback() {
        return Mono.just(buildFallbackResponse("product-service"));
    }

    /**
     * Fallback for order-service.
     * Triggered when /orders/** routes cannot reach order-service.
     * Configured in yml: fallbackUri: forward:/fallback/order
     */
    @RequestMapping("/fallback/order")
    public Mono<ResponseEntity<ErrorResponse>> orderFallback() {
        return Mono.just(buildFallbackResponse("order-service"));
    }

    /**
     * Fallback for inventory-service.
     * Triggered when /inventory/** routes cannot reach inventory-service.
     * Configured in yml: fallbackUri: forward:/fallback/inventory
     */
    @RequestMapping("/fallback/inventory")
    public Mono<ResponseEntity<ErrorResponse>> inventoryFallback() {
        return Mono.just(buildFallbackResponse("inventory-service"));
    }

    /**
     * Fallback for cart-service.
     * Triggered when /cart/** routes cannot reach cart-service.
     * Configured in yml: fallbackUri: forward:/fallback/cart
     */
    @RequestMapping("/fallback/cart")
    public Mono<ResponseEntity<ErrorResponse>> cartFallback() {
        return Mono.just(buildFallbackResponse("cart-service"));
    }

    /**
     * Builds a consistent ErrorResponse for any downed service.
     * 503 Service Unavailable is the correct HTTP status when a downstream is unreachable.
     */
    private ResponseEntity<ErrorResponse> buildFallbackResponse(String serviceName) {
        ErrorResponse errorResponse = new ErrorResponse(
                serviceName + " is currently unavailable. Please try again later.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errorResponse);
    }
}
