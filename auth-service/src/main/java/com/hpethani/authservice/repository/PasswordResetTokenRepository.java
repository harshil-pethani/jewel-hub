package com.hpethani.authservice.repository;

import com.hpethani.authservice.entity.PasswordResetToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("""
        delete from PasswordResetToken p
        where p.expiresAt < :now
    """)
    void deleteAllExpiredTokens(Instant now);
}
