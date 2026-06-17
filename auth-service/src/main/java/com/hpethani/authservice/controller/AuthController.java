package com.hpethani.authservice.controller;

import com.hpethani.authservice.dto.LoginRequest;
import com.hpethani.authservice.dto.LoginResponse;
import com.hpethani.authservice.dto.RegisterRequest;
import com.hpethani.authservice.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    /*
     * POST /auth/register
     *
     * 1. API Gateway → no AuthFilter on this route → forwarded directly
     * 2. JwtFilter   → shouldNotFilter() = true (/auth/*) → skipped
     * 3. SecurityConfig → /auth/** permitAll() → no auth check
     * 4. Controller -> @Valid → validates RegisterRequest fields (email, password, phone, etc.)
     * 5. AuthService.register() → duplicate email check → BCrypt hash password → save user
     * 6. Returns 201 Created
     */
    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request));
    }

    /*
     * POST /auth/login
     *
     * 1. API Gateway → no AuthFilter on this route → forwarded directly
     * 2. JwtFilter   → shouldNotFilter() = true (/auth/*) → skipped
     * 3. SecurityConfig → /auth/** permitAll() → no auth check
     * 4. Controller -> @Valid → validates LoginRequest (email, password)
     * 5. AuthService.login() → AuthenticationManager verifies email + BCrypt password
     *    → generates JWT with email, role, userId claims
     * 6. Returns 200 OK with { token, expiresIn }
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return ResponseEntity.ok(service.login(request, response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@CookieValue("refresh_token") String refreshToken, HttpServletResponse response) {
        return ResponseEntity.ok(service.refresh(refreshToken, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue("refresh_token") String refreshToken, HttpServletResponse response) {
        service.logout(refreshToken, response);
        return ResponseEntity.ok().build();
    }
}