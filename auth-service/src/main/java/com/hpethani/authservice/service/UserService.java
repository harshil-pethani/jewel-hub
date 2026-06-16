package com.hpethani.authservice.service;

import com.hpethani.authservice.dto.*;
import com.hpethani.authservice.entity.AccountStatus;
import com.hpethani.authservice.entity.User;
import com.hpethani.authservice.entity.UserAddress;
import com.hpethani.authservice.repository.UserAddressRepository;
import com.hpethani.authservice.repository.UserRepository;
import com.hpethani.commonconfig.exception.BadRequestException;
import com.hpethani.commonconfig.exception.ResourceNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserAddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       UserAddressRepository addressRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ─────────────────────────── PROFILE ────────────────────────────

    /**
     * Returns the profile of the currently authenticated user.
     * Email is extracted from the JWT by API Gateway and forwarded as "X-User-Email" header.
     */
    @Cacheable(
            value = "profile",
            key = "#userId"
    )
    public UserProfileResponse getProfile(String email, String userId) {
        User user = findActiveUserByEmail(email);
        return mapToProfileResponse(user);
    }

    /**
     * Updates firstName, lastName, phone of the authenticated user.
     */
    @CachePut(
            value = "profile",
            key = "#userId"
    )
    public UserProfileResponse updateProfile(String email, String userId, UpdateProfileRequest request) {
        User user = findActiveUserByEmail(email);

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhone(request.getPhone());

        return mapToProfileResponse(userRepository.save(user));
    }

    /**
     * Changes the user's password after verifying the current password.
     * Also validates that newPassword and confirmPassword match.
     */
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findActiveUserByEmail(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from current password");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * Soft-deletes the account. Sets status to DELETED.
     * The user's data is retained in DB for audit purposes.
     * Login will be blocked via CustomUserDetails.isEnabled().
     */
    @CacheEvict(
            value = "profile",
            key = "#userId"
    )
    public void deactivateAccount(String email, String userId) {
        User user = findActiveUserByEmail(email);
        user.setStatus(AccountStatus.DELETED);
        userRepository.save(user);
    }

    // ─────────────────────────── ADDRESSES ──────────────────────────

    /**
     * Returns all addresses for the authenticated user.
     */
    public List<AddressResponse> getAddresses(String email, String userId) {
        return addressRepository.findByUserEmail(email)
                .stream()
                .map(this::mapToAddressResponse)
                .toList();
    }

    /**
     * Returns single address by id for the authenticated user.
     */
    @Cacheable(
            value = "addresses",
            key = "#addressId"
    )
    public AddressResponse getSingleAddresses(String email, Long addressId) {
        UserAddress address = addressRepository.findByIdAndUserEmail(addressId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found with id: " + addressId));
        return mapToAddressResponse(address);
    }

    /**
     * Adds a new address for the user.
     * If isDefault=true, all other addresses are unset as default first.
     */
    @Transactional
    public AddressResponse addAddress(String email, AddressRequest request) {
        User user = findActiveUserByEmail(email);

        // Enforce single default — clear all existing defaults first
        if (request.isDefault()) {
            addressRepository.clearDefaultForUser(email);
        }

        UserAddress address = UserAddress.builder()
                .user(user)
                .label(request.getLabel())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .state(request.getState())
                .pincode(request.getPincode())
                .country(request.getCountry() != null ? request.getCountry() : "India")
                .isDefault(request.isDefault())
                .build();

        return mapToAddressResponse(addressRepository.save(address));
    }

    /**
     * Updates an existing address.
     * Only the owner can update — validated via email + id check.
     */
    @CachePut(
            value = "addresses",
            key = "#addressId"
    )
    @Transactional
    public AddressResponse updateAddress(String email, Long addressId, AddressRequest request) {
        UserAddress address = addressRepository.findByIdAndUserEmail(addressId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        if (request.isDefault()) {
            addressRepository.clearDefaultForUser(email);
        }

        address.setLabel(request.getLabel());
        address.setFullName(request.getFullName());
        address.setPhone(request.getPhone());
        address.setAddressLine1(request.getAddressLine1());
        address.setAddressLine2(request.getAddressLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPincode(request.getPincode());
        address.setCountry(request.getCountry() != null ? request.getCountry() : "India");
        address.setDefault(request.isDefault());

        return mapToAddressResponse(addressRepository.save(address));
    }

    /**
     * Deletes an address.
     * Only the owner can delete — validated via email + id check.
     */
    @CacheEvict(
            value = "addresses",
            key = "#addressId"
    )
    public void deleteAddress(String email, Long addressId) {
        UserAddress address = addressRepository.findByIdAndUserEmail(addressId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        addressRepository.delete(address);
    }

    /**
     * Sets the given address as the default for the user.
     * Clears all other defaults first.
     */
    @CachePut(
            value = "addresses",
            key = "#addressId"
    )
    @Transactional
    public AddressResponse setDefaultAddress(String email, Long addressId) {
        UserAddress address = addressRepository.findByIdAndUserEmail(addressId, email)
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));

        addressRepository.clearDefaultForUser(email);
        address.setDefault(true);

        return mapToAddressResponse(addressRepository.save(address));
    }

    // ─────────────────────────── HELPERS ────────────────────────────

    private User findActiveUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    private AddressResponse mapToAddressResponse(UserAddress address) {
        return AddressResponse.builder()
                .id(address.getId())
                .label(address.getLabel())
                .fullName(address.getFullName())
                .phone(address.getPhone())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .city(address.getCity())
                .state(address.getState())
                .pincode(address.getPincode())
                .country(address.getCountry())
                .isDefault(address.isDefault())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }
}

