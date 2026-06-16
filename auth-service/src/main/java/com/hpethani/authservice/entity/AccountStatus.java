package com.hpethani.authservice.entity;

public enum AccountStatus {
    ACTIVE,
    SUSPENDED,  // Temporarily disabled by admin
    DELETED     // Soft delete — account deactivated by user
}

