package com.hpethani.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user_addresses")
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning side — controls the foreign key column.
     * Always set this when creating an address.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // e.g. "Home", "Office", "Other"
    @Column(nullable = false)
    private String label;

    // Recipient name — may differ from account holder (gift orders, family)
    @Column(nullable = false)
    private String fullName;

    // Delivery contact number
    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String addressLine1;

    // Apartment, floor, landmark — optional
    @Column
    private String addressLine2;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String state;

    @Column(nullable = false)
    private String pincode;

    @Column(nullable = false)
    @Builder.Default
    private String country = "India";

    /**
     * Only one address per user can be default.
     * Service layer enforces this by unsetting previous default on update.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

