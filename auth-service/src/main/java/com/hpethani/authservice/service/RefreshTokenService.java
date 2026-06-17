package com.hpethani.authservice.service;

import com.hpethani.authservice.entity.RefreshToken;
import com.hpethani.authservice.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository repository;

    public String generateRefreshToken(Long userId) {
        // This rawToken is saved in cookie. In DB we have to store it as hashed.
        // This way, even if the database is compromised, attackers do not obtain usable tokens.
        String rawToken = this.generateRawToken();

        // refreshToken instance contains hashed Token
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(this.generateHashedToken(rawToken))
                .expiresAt(Instant.now().plus(DAYS, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        repository.save(refreshToken);
        return rawToken;
    }

    private static final long DAYS = 30;

    // Create 64-byte array
    //      ↓
    // Fill with SecureRandom bytes
    //      ↓
    // Convert bytes to URL-safe Base64
    //      ↓
    // Remove "=" padding
    //      ↓
    // Return token string
    public String generateRawToken() {
        // byte array with size 64. Initially all elements are 0.
        byte[] bytes = new byte[64];
        // secureRandom is typically an instance of java.security.SecureRandom.
        // nextBytes(bytes) fills the array with cryptographically secure random values. [-45, 12, 89, -101, ...]
        secureRandom.nextBytes(bytes);

        // Gets a URL-safe Base64 encoder.
        // It replaces + with -, / with _
        // This prevents problems when using the token in: URLs, Query parameters, HTTP headers
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    // SHA-256 always produces: 256 bits = 32 bytes (i.e., harshil -> 32 bytes, harshilpethanilongstring -> 32 bytes)
    public String generateHashedToken(String token) {
        try {
            // Creates a SHA-256 hashing engine. digest is an object capable of computing hashes.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Hashing algorithm required bytes -> to hash the token
            // So convert token to byte first -> token.getBytes(StandardCharsets.UTF_8)
            // since this is SHA-256 algorithm output will be 32 bytes.
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));

            // hash is binary data not human-readable. So converts it to hexadecimal.
            // Each byte becomes 2 hex characters. so 32 bytes -> 64 Characters long String.
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}