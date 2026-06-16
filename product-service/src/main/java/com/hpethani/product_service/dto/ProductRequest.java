package com.hpethani.product_service.dto;

import com.hpethani.product_service.enums.DiamondType;
import com.hpethani.product_service.enums.MaterialType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Diamond type is required")
    private DiamondType diamond;

    @NotNull(message = "Material type is required")
    private MaterialType material;

    @Min(value = 0, message = "Base price must be >= 0")
    private double basePrice;

    @Min(value = 0, message = "Base price must be >= 0")
    private double discountedPrice;

    @NotNull(message = "Category is required")
    private Set<ProductCategoryDto> categories;

    @NotNull(message = "images is required")
    private Set<ProductImageDto> images;

    @Min(value = 0, message = "Quantity must be >= 0")
    private int quantity;
}
