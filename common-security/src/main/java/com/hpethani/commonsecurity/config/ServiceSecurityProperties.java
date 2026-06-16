package com.hpethani.commonsecurity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * ServiceSecurityProperties — allows each service to declare its own public paths
 * via application.properties, without modifying the shared module.
 *
 * Usage in any service's application.properties:
 *
 *   # Default public paths (always included automatically):
 *   #   /actuator/health, /actuator/info
 *   #
 *   # Add service-specific public paths:
 *   service.security.public-paths=/actuator/health,/actuator/info
 *
 * If not configured, defaults to actuator health endpoints only.
 */
@ConfigurationProperties(prefix = "service.security")
public class ServiceSecurityProperties {

    /**
     * List of URL patterns that are accessible without authentication.
     * Defaults include actuator health/info for load balancer probes.
     */
    private List<String> publicPaths = new ArrayList<>(List.of(
            "/actuator/health",
            "/actuator/info",
            "/error"           // Spring Boot's error forwarding path — must be public.
                               // When any error occurs, Spring internally forwards to /error.
                               // Without this, SecurityFilterChain blocks /error → 401 hides the real error.
    ));

    /**
     * Shared secret between API Gateway and all downstream services.
     * Gateway stamps every forwarded request with "X-Gateway-Secret: <value>".
     * Services reject requests where this header is missing or wrong.
     *
     * Configure in each service's application.properties:
     *   service.security.gateway-secret=your-long-random-secret
     *
     * Must be the SAME value in api-gateway and all services.
     * In production, inject via environment variable — never hardcode.
     */
    private String gatewaySecret = "";

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public String getGatewaySecret() {
        return gatewaySecret;
    }

    public void setGatewaySecret(String gatewaySecret) {
        this.gatewaySecret = gatewaySecret;
    }
}

