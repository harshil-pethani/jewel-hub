package com.hpethani.product_service.dto;

import com.hpethani.product_service.enums.DiamondType;
import com.hpethani.product_service.enums.MaterialType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private String title;
    private String description;
    private DiamondType diamond;
    private MaterialType materialType;
    private double basePrice;
    private double discountedPrice;
    private Set<ProductCategoryDto> categories;
    private Set<ProductImageDto> images;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
