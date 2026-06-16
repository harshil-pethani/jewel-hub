package com.hpethani.commonsecurity.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * GatewayHeaderAuthFilter — reads the identity headers injected by API Gateway's AuthFilter
 * and populates Spring Security's SecurityContext.
 *
 * Flow:
 *   Client → API Gateway (validates JWT, sets "email" + "roles" + "X-Gateway-Secret" headers)
 *         → Downstream Service (this filter reads headers → sets SecurityContext)
 *
 * Security: Before trusting ANY identity header, this filter verifies the
 * "X-Gateway-Secret" header matches the configured shared secret.
 * This prevents anyone from bypassing the gateway by hitting service ports directly
 * and manually setting "email" / "roles" headers to impersonate users.
 *
 * Headers read:
 *   - "X-Gateway-Secret" → must match service.security.gateway-secret (verified first)
 *   - "email"            → becomes the authentication principal (e.g., "john@example.com")
 *   - "roles"            → comma-separated roles (e.g., "ROLE_USER" or "ROLE_USER,ROLE_ADMIN")
 *   - "userid"           → stored as credential (available via authentication.getCredentials())
 */
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    // Shared secret configured in each service's application.properties
    // Must match the value the API Gateway stamps on every forwarded request
    private final String gatewaySecret;

    public GatewayHeaderAuthFilter(String gatewaySecret) {
        this.gatewaySecret = gatewaySecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String incomingSecret = request.getHeader("X-Gateway-Secret");

        // Reject immediately if secret is missing or wrong — this is direct port access.
        // Even if someone sets "email" header manually, they cannot forge the secret.
        if (gatewaySecret == null || gatewaySecret.isBlank()
                || !gatewaySecret.equals(incomingSecret)) {
            filterChain.doFilter(request, response);
            return;
        }

        String email = request.getHeader("email");
        String rolesHeader = request.getHeader("roles");
        String userId = request.getHeader("userid");

        // Only set auth if email header is present (set by Gateway after JWT validation)
        // If missing → no SecurityContext auth → Spring Security will block on protected endpoints
        if (email != null && !email.isBlank() && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Parse roles: "ROLE_USER" → [ROLE_USER] or "ROLE_USER,ROLE_ADMIN" → [ROLE_USER, ROLE_ADMIN]
            List<SimpleGrantedAuthority> authorities = parseAuthorities(rolesHeader);

            // Create authentication token — no password needed (gateway already verified)
            // principal = email, credentials = userId (may be null)
            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(email, userId, authorities);

            // Set auth details in auth-token. So that whenever we want to find which request handled authentication and added this authToken
            // by using SecurityContextHolder.getContext().getAuthentication().getDetails()
            // (like req info, client ip, etc.)
            // Attach request details (IP, session) — useful for audit logging
            // Available via: SecurityContextHolder.getContext().getAuthentication().getDetails()
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // Spring Security 6: create a NEW SecurityContext and SET it on the holder.
            // Do NOT mutate the existing context via getContext().setAuthentication().
            // SecurityContextHolderFilter sets an empty context before our filter runs —
            // mutating it in-place is not reliably propagated in Spring Security 6.
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authToken);
            SecurityContextHolder.setContext(context);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Parse the roles header into Spring Security GrantedAuthority list.
     * Handles: null, blank, single role, comma-separated multiple roles.
     * Each role is trimmed to handle "ROLE_USER, ROLE_ADMIN" (with spaces).
     *
     * Ensures each authority has the "ROLE_" prefix required by Spring Security's
     * hasRole() / hasAnyRole() checks. Supports both "USER" and "ROLE_USER" formats
     * so that old tokens (without prefix) and new tokens (with prefix) both work.
     */
    private List<SimpleGrantedAuthority> parseAuthorities(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                // Ensure ROLE_ prefix — Spring Security hasRole()/hasAnyRole() requires it
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
