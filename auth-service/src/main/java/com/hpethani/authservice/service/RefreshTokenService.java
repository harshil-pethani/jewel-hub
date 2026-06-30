package com.hpethani.authservice.service;

import com.hpethani.authservice.entity.RefreshToken;
import com.hpethani.authservice.repository.RefreshTokenRepository;
import com.hpethani.commonconfig.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

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

    private final CommonUtilityService commonUtilityService;
    private final RefreshTokenRepository repository;
    private static final long DAYS = 30;

    public String generateRefreshToken(Long userId) {
        // This rawToken is saved in cookie. In DB we have to store it as hashed.
        // This way, even if the database is compromised, attackers do not obtain usable tokens.
        String rawToken = commonUtilityService.generateRawToken(64);

        // refreshToken instance contains hashed Token
        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(commonUtilityService.generateHashedToken(rawToken))
                .expiresAt(Instant.now().plus(DAYS, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        repository.save(refreshToken);
        return rawToken;
    }

    @Transactional(noRollbackFor = UnauthorizedException.class)
    public void revokeAllUserTokens(Long userId) {
        repository.revokeAllByUserId(userId);
    }
}