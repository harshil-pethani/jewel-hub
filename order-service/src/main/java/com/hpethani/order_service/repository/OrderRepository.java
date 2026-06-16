package com.hpethani.order_service.repository;

import com.hpethani.order_service.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Fetch orders for a specific user with items eagerly loaded
    @EntityGraph(attributePaths = {"orderItems"})
    Page<Order> findByUserEmail(String userEmail, Pageable pageable);

    // Fetch single order with items — used in getOrderById
    @EntityGraph(attributePaths = {"orderItems"})
    Optional<Order> findByIdAndUserEmail(Long id, String userEmail);
}
