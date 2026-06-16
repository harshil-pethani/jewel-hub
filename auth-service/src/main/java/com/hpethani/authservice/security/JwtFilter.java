package com.hpethani.authservice.security;

import com.hpethani.authservice.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the "email" header forwarded by the API Gateway and populates
 * the Spring Security context for this service.
 *
 * WHY read headers instead of the JWT directly?
 *  - The API Gateway has ALREADY validated the JWT (signature + expiry).
 *  - Re-parsing the JWT here would be redundant work.
 *  - We trust the gateway (internal network) — it would not set the "email"
 *    header unless it had a valid token.
 *  - This is consistent with how all other downstream services work via
 *    common-security's GatewayHeaderAuthFilter.
 *
 * WHY still do a DB lookup (loadUserByUsername)?
 *  - To get proper GrantedAuthority objects (needed for hasAnyRole() checks).
 *  - To verify account status: SUSPENDED/DELETED users are blocked via
 *    CustomUserDetails.isEnabled() / isAccountNonLocked().
 *
 * Direct port access (bypassing gateway):
 *  - "email" header will be absent → SecurityContext stays empty
 *  - Spring Security blocks with 401 → JwtAuthEntryPoint returns JSON error
 */
@Component
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final CustomUserDetailsService userDetailsService;

    // Must match gateway.internal.secret in api-gateway's application.properties
    @Value("${gateway.internal.secret:}")
    private String gatewaySecret;

    public JwtFilter(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    /**
     * Skip filter entirely for public auth endpoints (/auth/login, /auth/register).
     * These are permitAll() in SecurityConfig and never carry identity headers.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Read the "email" header set by the API Gateway's AuthFilter
        String email = request.getHeader("email");

        // Verify gateway secret first — if missing or wrong, this is direct port access.
        // Do NOT trust the email header without this check.
        String incomingSecret = request.getHeader("X-Gateway-Secret");
        if (gatewaySecret == null || gatewaySecret.isBlank()
                || !gatewaySecret.equals(incomingSecret)) {
            filterChain.doFilter(request, response);
            return;
        }

        // If no email header after secret check: proceed without auth
        if (email == null || email.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only set auth if SecurityContext doesn't already have one
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Load user from DB: validates account status (ACTIVE/SUSPENDED/DELETED)
                // and provides proper GrantedAuthority objects for Spring Security role checks
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                // Set auth details in auth-token. So that whenever we want to find which request handled authentication and added this authToken
                // by using SecurityContextHolder.getContext().getAuthentication().getDetails()
                // (like req info, client ip, etc.)
                // Attach request details (IP, session) — useful for audit logging
                // Available via: SecurityContextHolder.getContext().getAuthentication().getDetails()
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // Spring Security 6: create a NEW SecurityContext and SET it.
                // Do NOT mutate via getContext().setAuthentication() — not reliably
                // propagated in Spring Security 6's SecurityContextHolderFilter.
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authToken);
                SecurityContextHolder.setContext(context);
            } catch (Exception e) {
                    // Log so we can see WHY authentication failed — previously this was silently swallowed.
                    // Common causes: user not found in DB, account deleted, DB connection issue.
                    log.error("Failed to authenticate request for email='{}': {}", email, e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}