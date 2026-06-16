package com.hpethani.product_service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductImageDto {
    private Long id;
    @NotNull(message = "Product Image is required")
    private String imageUrl;
    @NotNull(message = "Thumbnail flag is required")
    private Boolean thumbnail;
}
