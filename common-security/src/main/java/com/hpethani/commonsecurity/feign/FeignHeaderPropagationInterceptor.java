package com.hpethani.commonsecurity.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;

/**
 * FeignHeaderPropagationInterceptor — propagates security headers on ALL outgoing Feign calls.
 *
 * Problem it solves:
 *   When service A calls service B via Feign (service-to-service), the call goes DIRECTLY
 *   — it does NOT pass through the API Gateway. This means:
 *     - No X-Gateway-Secret header → GatewayHeaderAuthFilter sees direct-access attempt
 *     - No email/roles headers      → No identity set in SecurityContext
 *   Result: service B rejects the call with 401 Unauthorized.
 *
 * Solution:
 *   This interceptor adds the required headers to every outgoing Feign request:
 *     - X-Gateway-Secret → proves this is an internal trusted call (not external bypass)
 *     - email            → forwards the original requesting user's identity
 *     - roles            → forwards the original user's roles for downstream authorization
 *     - userid           → forwards the original user's DB id
 *
 * How it works:
 *   - Registered as a global Feign RequestInterceptor (Spring auto-discovers it)
 *   - Reads headers from the CURRENT HttpServletRequest (the request currently being handled)
 *   - Spring injects HttpServletRequest as a scoped proxy → always returns the active request
 *   - If no active request (e.g., background thread), ObjectProvider.getIfAvailable() returns null → skipped
 *
 * Auto-configured by CommonSecurityAutoConfig only when feign.RequestInterceptor is on classpath.
 */
public class FeignHeaderPropagationInterceptor implements RequestInterceptor {

    private final String gatewaySecret;

    /**
     * ObjectProvider<HttpServletRequest> gives us access to the request-scoped bean.
     * Using ObjectProvider instead of direct injection avoids errors when no request
     * is in scope (e.g., scheduled tasks, background threads).
     */
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public FeignHeaderPropagationInterceptor(String gatewaySecret,
                                              ObjectProvider<HttpServletRequest> requestProvider) {
        this.gatewaySecret = gatewaySecret;
        this.requestProvider = requestProvider;
    }

    @Override
    public void apply(RequestTemplate template) {
        // Stamp the gateway secret so the receiving service trusts this as an internal call
        if (gatewaySecret != null && !gatewaySecret.isBlank()) {
            template.header("X-Gateway-Secret", gatewaySecret);
        }

        // Forward user identity from the current incoming request
        // getIfAvailable() returns null if no HTTP request is currently in scope
        HttpServletRequest currentRequest = requestProvider.getIfAvailable();
        if (currentRequest == null) {
            return;
        }

        forwardIfPresent(template, currentRequest, "email");
        forwardIfPresent(template, currentRequest, "roles");
        forwardIfPresent(template, currentRequest, "userid");
    }

    private void forwardIfPresent(RequestTemplate template, HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        if (value != null && !value.isBlank()) {
            template.header(headerName, value);
        }
    }
}

