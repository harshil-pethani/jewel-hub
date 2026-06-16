package com.hpethani.apigateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * JwtUtil handles all JWT operations at the API Gateway level.
 *
 * The gateway does NOT generate tokens (that's auth-service's job).
 * It only validates and reads claims from incoming tokens.
 *
 * IMPORTANT: The secret key here MUST match the one in auth-service exactly.
 * Both services read from the same config (or shared config server in production).
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    /**
     * Builds the HMAC-SHA signing key from the configured secret string.
     * Key must be at least 256 bits (32 characters) for HS256.
     */
    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    /**
     * Validates the token signature and expiry.
     * Throws JwtException if token is expired, malformed, or has wrong signature.
     */
    public void validateToken(String token) {
        Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token); // parseClaimsJws() is deprecated — use parseSignedClaims()
    }

    /**
     * Extracts all claims (payload) from the token.
     * Only call AFTER validateToken() to ensure token is trusted.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extracts the user's email (subject) from the token.
     * Forwarded to downstream services as X-User-Email header.
     */
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Extracts the user's role(s) from the token claims.
     * Forwarded to downstream services as X-User-Roles header.
     * Returns empty string if no roles claim is present.
     */
    public String extractRoles(String token) {
        Claims claims = extractAllClaims(token);
        Object roles = claims.get("roles");
        if (roles instanceof List<?>) {
            return String.join(",", ((List<?>) roles).stream().map(Object::toString).toList());
        }
        // Support single role stored as a string
        return roles != null ? roles.toString() : "";
    }

    public String extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return (String) claims.get("userId");
    }
}
