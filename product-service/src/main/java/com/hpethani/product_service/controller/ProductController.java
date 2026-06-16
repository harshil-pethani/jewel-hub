package com.hpethani.product_service.controller;

import com.hpethani.commonconfig.exception.BadRequestException;
import com.hpethani.product_service.dto.ProductRequest;
import com.hpethani.product_service.dto.ProductResponse;
import com.hpethani.product_service.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/product")
public class ProductController {

    private final ProductService productService;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")  // only ADMIN can create products
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(request));
    }

    @GetMapping
    public Page<ProductResponse> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        // Whitelist allowed sort fields — prevents InvalidDataAccessApiUsageException
        // if client passes a field that doesn't exist on Product entity
        Set<String> allowedSortFields = Set.of("id", "title", "basePrice", "materialType", "createdAt");
        if (!allowedSortFields.contains(sortBy)) {
            throw new BadRequestException("Invalid sortBy field: " + sortBy + ". Allowed: " + allowedSortFields);
        }
        return productService.getAllProducts(page, size, sortBy, sortDir);
    }

    @GetMapping("/{productId}")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<ProductResponse> getProductById(@PathVariable String productId) {
        return ResponseEntity.ok(productService.getSingleProduct(parseId(productId)));
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")  // only ADMIN can delete products
    public ResponseEntity<String> deleteProductById(@PathVariable String productId) {
        productService.deleteProductById(parseId(productId));
        return ResponseEntity.ok("Product with id: " + productId + " has been deleted");
    }

    @PutMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")  // only ADMIN can delete products
    public ResponseEntity<ProductResponse> updateProductById(
            @PathVariable String productId,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(parseId(productId), request));
    }

    // Reusable ID parser — throws BadRequestException instead of 500 on invalid input
    private long parseId(String rawId) {
        try {
            return Long.parseLong(rawId);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Invalid product id: " + rawId);
        }
    }
}