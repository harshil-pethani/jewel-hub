package com.hpethani.order_service.entity;

import com.hpethani.commonconfig.entity.AuditableEntity;
import com.hpethani.order_service.enums.OrderStatus;
import com.hpethani.order_service.enums.PaymentMethod;
import com.hpethani.order_service.enums.PaymentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "orders")
public class Order extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // UUID-based order number shown to customer (e.g. ORD-a1b2c3d4)
    @Column(unique = true, nullable = false)
    private String orderNumber;

    // User identified by email from JWT (forwarded by API Gateway as "email" header)
    @Column(nullable = false)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    // TODO: Add online payment support when payment gateway is integrated
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod;

    // COD: always PENDING until delivery agent marks it PAID
    // TODO: Update to PAID/FAILED based on payment gateway callback
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String addressType;

    // Shipping address captured at order time (snapshot — user can change address later, order unaffected)
    @Column(nullable = false)
    private String shippingAddress;

    @Column(nullable = false)
    private String pincode;;

    // Snapshot of cart total at time of order
    @Column(nullable = false)
    private double totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> orderItems = new ArrayList<>();
}