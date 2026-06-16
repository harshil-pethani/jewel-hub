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
        String rawToken = this.generateRawToken();
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

    public String generateRawToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    public String generateHashedToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}