package com.hpethani.product_service.repository;

import com.hpethani.product_service.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product,Long>, JpaSpecificationExecutor<Product> {

    // Eagerly fetch categories and images in a single query to avoid N+1 problem.
    @EntityGraph(attributePaths = {
            "categories",
            "images"
    })
    Page<Product> findAll(Pageable pageable);
}
