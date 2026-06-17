package com.hpethani.authservice.service;

import com.hpethani.authservice.dto.LoginRequest;
import com.hpethani.authservice.dto.LoginResponse;
import com.hpethani.authservice.dto.RegisterRequest;
import com.hpethani.authservice.entity.CustomUserDetails;
import com.hpethani.authservice.entity.RefreshToken;
import com.hpethani.authservice.entity.Role;
import com.hpethani.authservice.entity.User;
import com.hpethani.authservice.repository.RefreshTokenRepository;
import com.hpethani.authservice.repository.UserRepository;
import com.hpethani.authservice.security.JwtService;
import com.hpethani.commonconfig.exception.BadRequestException;
import com.hpethani.commonconfig.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;

    public String register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("User already exists");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(Role.USER) // Always USER — role is never trusted from client
                .build();

        userRepository.save(user);

        return "User registered successfully";
    }

    public LoginResponse login(LoginRequest request, HttpServletResponse response) {
        try {
            //    authenticationManager.authenticate(email + rawPassword)
            //          ↓
            //    ProviderManager (AuthenticationManager impl)
            //          ↓
            //    DaoAuthenticationProvider
            //          ↓
            //    ┌─────────────────────────────────────────────┐
            //    │ 1. loadUserByUsername(email)                │
            //    │    → CustomUserDetailsService               │
            //    │    → UserRepository.findByEmail()  (DB hit) │
            //    │    → returns CustomUserDetails              │
            //    │                                             │
            //    │ 2. passwordEncoder.matches(raw, hash)       │
            //    │    → BCryptPasswordEncoder                  │
            //    │    → compares with DB stored hash           │
            //    │                                             │
            //    │ 3. isEnabled() / isAccountNonLocked()       │
            //    │    → CustomUserDetails checks AccountStatus │
            //    └─────────────────────────────────────────────┘
            //          ↓
            //    Returns authenticated token (principal = CustomUserDetails)
            //          ↓
            //    AuthService casts to CustomUserDetails → generateToken()

            // AuthenticationManager internally:
            //  1. Calls UserDetailsService.loadUserByUsername(email)
            //  2. Uses PasswordEncoder.matches(rawPassword, storedHash)
            //  3. Throws BadCredentialsException if either step fails

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // Principal is CustomUserDetails returned by CustomUserDetailsService
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            String accessToken = jwtService.generateToken(userDetails, userDetails.getUserId());
            String refreshToken = refreshTokenService.generateRefreshToken(userDetails.getUserId());

            addRefreshCookie(response, refreshToken);

            return LoginResponse.builder()
                    .accessToken(accessToken)
                    .expiresIn(jwtService.getExpirationSeconds())
                    .build();

        } catch (BadCredentialsException e) {
            throw new BadRequestException("Invalid email or password");
        }
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken, HttpServletResponse response) {
        // Generate hashed token of current token.
        String hash = refreshTokenService.generateHashedToken(rawRefreshToken);
        // Check if it is existed ?
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash)
                                .orElseThrow(
                                        () -> new UnauthorizedException("Invalid token")
                                );
        // Check if it is revoked ?
        if (token.isRevoked()) {
            throw new UnauthorizedException("Token revoked");
        }
        // Check if it is expired ?
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Expired token");
        }
        // Now set it as revoked and save to DB. As we will store new refresh Token.
        token.setRevoked(true);
        refreshTokenRepository.save(token);

        Long userId = token.getUserId();
        User user = userRepository.findById(userId).orElseThrow();

        // Generate new JWT token as well as refresh token.
        String newAccessToken = jwtService.generateToken(userDetailsMapper(user), userId);
        String newRefreshToken = refreshTokenService.generateRefreshToken(userId);

        // Set refreshToken in response cookie. This will overwrite the old refresh token cookie.
        addRefreshCookie(response, newRefreshToken);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .expiresIn(900)
                .build();
    }

    @Transactional
    public void logout(String rawToken, HttpServletResponse response) {
        // Generate hashed token of current token.
        String hash = refreshTokenService.generateHashedToken(rawToken);
        // Check if it is existed ? If exists, set revoked = true and save to DB. This will prevent any future use of this token.
        refreshTokenRepository.findByTokenHash(hash)
                                .ifPresent(token -> {
                                    token.setRevoked(true);
                                    refreshTokenRepository.save(token);
                                });
        // clear the existing refreshToken from cookie.
        clearCookie(response);
    }

    private void clearCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                                    .httpOnly(true)
                                    .secure(true)
                                    .path("/auth")
                                    .maxAge(0)
                                    .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private UserDetails userDetailsMapper(User user) {
        return new CustomUserDetails(user);
    }

    private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        // ResponseCookie.from("refresh_token", refreshToken) => key = refresh_token, value = tokenValue
        // .httpOnly(true) => JavaScript cannot access this cookie by document.cookie (prevents XSS attacks)
        // .secure(true) => cookie is sent only over HTTPS (prevents MITM attacks) (for locally we have to set it false)
        // .sameSite("Strict") => Cookie sent only when navigating within the same site.
        // .path("/auth") => Cookie is only sent for URLs beginning with:
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                                    .httpOnly(true)
                                    .secure(true)
                                    .sameSite("Strict")
                                    .path("/auth")
                                    .maxAge(Duration.ofDays(30))
                                    .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
