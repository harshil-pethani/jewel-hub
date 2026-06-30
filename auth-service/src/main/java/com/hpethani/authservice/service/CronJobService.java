package com.hpethani.authservice.service;

import com.hpethani.authservice.repository.PasswordResetTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class CronJobService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanup() {

        passwordResetTokenRepository.deleteAllExpiredTokens(
                Instant.now()
        );
    }
}