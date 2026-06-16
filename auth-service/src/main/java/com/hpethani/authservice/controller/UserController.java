package com.hpethani.authservice.controller;

import com.hpethani.authservice.dto.*;
import com.hpethani.authservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * All endpoints require a valid JWT.
 * The API Gateway extracts the email from the JWT and forwards it as the "email" header.
 * We read it here instead of parsing the JWT again — no duplication.
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ─────────────────────────── PROFILE ────────────────────────────

    /**
     * GET /api/user/profile
     * Returns the authenticated user's profile.
     */
    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> getProfile(
            @RequestHeader("email") String email, @RequestHeader("userId") String userId) {
        return ResponseEntity.ok(userService.getProfile(email, userId));
    }

    /**
     * PUT /api/user/profile
     * Updates firstName, lastName, phone.
     */
    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @RequestHeader("email") String email,
            @RequestHeader("userId") String userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(email, userId, request));
    }

    /**
     * PUT /api/user/change-password
     * Changes the authenticated user's password.
     */
    @PutMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @RequestHeader("email") String email,
            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(email, request);
        return ResponseEntity.ok("Password changed successfully");
    }

    /**
     * DELETE /api/user/account
     * Soft-deletes (deactivates) the user's account.
     * The account cannot be logged into after this.
     */
    @DeleteMapping("/account")
    public ResponseEntity<String> deactivateAccount(
            @RequestHeader("email") String email, @RequestHeader("userId") String userId) {
        userService.deactivateAccount(email, userId);
        return ResponseEntity.ok("Account deactivated successfully");
    }

    // ─────────────────────────── ADDRESSES ──────────────────────────

    /**
     * GET /api/user/addresses
     * Returns all saved addresses for the authenticated user.
     */
    @GetMapping("/addresses")
    public ResponseEntity<List<AddressResponse>> getAddresses(
            @RequestHeader("email") String email, @RequestHeader("userId") String userId) {
        return ResponseEntity.ok(userService.getAddresses(email, userId));
    }

    /**
     * GET /api/user/addresses/{addressId}
     * Returns single address by id for the authenticated user.
     */
    @GetMapping("/addresses/{addressId}")
    public ResponseEntity<AddressResponse> getSingleAddresses(
            @RequestHeader("email") String email,
            @PathVariable Long addressId) {
        return ResponseEntity.ok(userService.getSingleAddresses(email, addressId));
    }

    /**
     * POST /api/user/addresses
     * Adds a new address.
     */
    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> addAddress(
            @RequestHeader("email") String email,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.addAddress(email, request));
    }

    /**
     * PUT /api/user/addresses/{id}
     * Updates an existing address. Only the owner can update.
     */
    @PutMapping("/addresses/{id}")
    public ResponseEntity<AddressResponse> updateAddress(
            @RequestHeader("email") String email,
            @PathVariable Long id,
            @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.ok(userService.updateAddress(email, id, request));
    }

    /**
     * DELETE /api/user/addresses/{id}
     * Deletes an address. Only the owner can delete.
     */
    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<String> deleteAddress(
            @RequestHeader("email") String email,
            @PathVariable Long id) {
        userService.deleteAddress(email, id);
        return ResponseEntity.ok("Address deleted successfully");
    }

    /**
     * PUT /api/user/addresses/{id}/default
     * Sets the given address as default. Clears any previous default.
     */
    @PutMapping("/addresses/{id}/default")
    public ResponseEntity<AddressResponse> setDefaultAddress(
            @RequestHeader("email") String email,
            @PathVariable Long id) {
        return ResponseEntity.ok(userService.setDefaultAddress(email, id));
    }
}

