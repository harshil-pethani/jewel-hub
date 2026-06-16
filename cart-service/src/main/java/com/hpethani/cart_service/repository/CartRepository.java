package com.hpethani.cart_service.repository;

import com.hpethani.cart_service.entity.Cart;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // Eagerly fetch items with the cart to avoid LazyInitializationException
    @EntityGraph(attributePaths = {"items"})
    Optional<Cart> findByUserEmail(String userEmail);
}

