package com.hpethani.apigateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;
import java.util.function.Predicate;


/**
 * Validator is responsible for determining which routes are "secured" (require JWT)
 * and which are "open" (public endpoints like login/register).
 *
 * Used by AuthFilter to decide whether to check Authorization header or skip.
 */
@Component
public class Validator {

    // AntPathMatcher supports wildcard patterns like /auth/** or /auth/validate-token/{token}
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    /**
     * List of public endpoints that do NOT require a JWT token.
     * Ant-style patterns: /auth/** covers /auth/login, /auth/register, etc.
     */
    public static final List<String> OPEN_ENDPOINTS = List.of(
            "POST:/auth/**",
            "GET:/api/product/**"
    );


    /**
     * Predicate that returns TRUE if the request is secured (needs JWT validation).
     * Returns FALSE if the path matches any open/public endpoint.
     *
     * NOTE: AntPathMatcher.match(pattern, path) — pattern is FIRST, path is SECOND.
     * Previous bug had them swapped, causing ALL routes to be treated as secured.
     */
    public Predicate<ServerHttpRequest> isSecured = request -> {

        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        return OPEN_ENDPOINTS.stream()
                .noneMatch(endpoint -> {

                    String[] parts = endpoint.split(":", 2);

                    if (parts.length != 2) {
                        return false;
                    }

                    String configuredMethod = parts[0];
                    String configuredPath = parts[1];

                    return configuredMethod.equalsIgnoreCase(method)
                            && antPathMatcher.match(configuredPath, path);
                });
    };
}
