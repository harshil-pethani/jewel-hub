package com.hpethani.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Handles JWT generation and validation for auth-service.
 *
 * Responsibilities:
 *  - generateToken(): called on login — embeds email, role, userId into token
 *  - isTokenValid(): called by JwtFilter to validate incoming tokens
 *  - extractAllClaims(): parses the token payload
 *
 * NOTE: The secret key MUST match the one in api-gateway's JwtUtil exactly.
 * Both read from the same config property: jwt.secret
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration-seconds}")
    private long expirationSeconds;

    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /**
     * Generates a JWT token.
     *
     * Claims embedded:
     *  - subject  → user's email (standard JWT "sub" claim)
     *  - roles    → user's role string, e.g. "USER" or "ADMIN"
     *  - userId   → user's DB id — forwarded by gateway as "userid" header
     *
     * @param userDetails Spring Security UserDetails (email + authorities)
     * @param userId      the user's database ID
     */
    public String generateToken(UserDetails userDetails, Long userId) {
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("USER");

        return Jwts.builder()
                .subject(userDetails.getUsername())       // email
                .claim("roles", "ROLE_" + role)           // "ROLE_USER" or "ROLE_ADMIN" — with prefix so GatewayHeaderAuthFilter creates correct GrantedAuthority
                .claim("userId", String.valueOf(userId))  // e.g. "5"
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationSeconds * 1000))
                .signWith(getSignKey())
                .compact();
    }


    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}