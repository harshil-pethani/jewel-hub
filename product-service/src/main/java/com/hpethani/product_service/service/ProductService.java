package com.hpethani.product_service.service;

import com.hpethani.commonconfig.exception.BadRequestException;
import com.hpethani.commonconfig.exception.CustomException;
import com.hpethani.commonconfig.exception.ResourceNotFoundException;
import com.hpethani.product_service.client.InventoryServiceClient;
import com.hpethani.product_service.dto.*;
import com.hpethani.product_service.entity.Category;
import com.hpethani.product_service.entity.Product;
import com.hpethani.product_service.entity.ProductImage;
import com.hpethani.product_service.repository.CategoryRepository;
import com.hpethani.product_service.repository.ProductRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryServiceClient inventoryServiceClient;

    // ── Public API ─────────────────────────────────────────────────────────────

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Set<Category> categories = validateAndFetchCategories(request.getCategories());

        // Build product WITHOUT images first
        // Images must be linked AFTER save so the product has a real DB-generated ID.
        // If images are attached to a transient (unsaved) product, ProductImage.product = null
        // → Hibernate inserts them with product_id = NULL because the owning side is not set.
        Product product = Product.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .diamond(request.getDiamond())
                .material(request.getMaterial())
                .basePrice(request.getBasePrice())
                .discountedPrice(request.getDiscountedPrice())
                .categories(categories)
                .images(new HashSet<>())  // empty — images added after save
                .active(true)
                .build();

        log.info("Creating jewellery product: {}", request.getTitle());

        Product savedProduct;
        try {
            // Step 1: Save product to get the generated ID
            savedProduct = productRepository.save(product);
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation while creating product: {}", e.getMostSpecificCause().getMessage());
            throw new BadRequestException("A product with the same unique field already exists");
        } catch (Exception e) {
            log.error("Unexpected error while saving product: {}", e.getMessage());
            throw new CustomException("Failed to create product: " + e.getMessage());
        }

        // Step 2: Now link images with the SAVED product (has a real ID)
        // buildNewImages sets ProductImage.product = savedProduct (owning side) so product_id is set correctly
        if (request.getImages() != null && !request.getImages().isEmpty()) {
            validateImageUrls(request.getImages());
            savedProduct.setImages(buildNewImages(request.getImages(), savedProduct));
            savedProduct = productRepository.save(savedProduct); // cascade saves images with correct product_id
        }

        // Register product stock in inventory-service
        // TODO: handle partial failure — consider SAGA / outbox pattern
        try {
            inventoryServiceClient.createInventory(
                    InventoryRequest.builder()
                            .productId(savedProduct.getId())
                            .availableQuantity(request.getQuantity())
                            .build()
            );
        } catch (FeignException e) {
            log.error("Inventory service error for product id={}: status={} message={}",
                    savedProduct.getId(), e.status(), e.getMessage());
            throw new CustomException("Product creation failed — inventory service returned an error (transaction rolled back)");
        } catch (Exception e) {
            log.error("Failed to reach inventory service for product id={}: {}", savedProduct.getId(), e.getMessage());
            throw new CustomException("Product creation failed — inventory service unreachable (transaction rolled back)");
        }

        return mapToProductResponse(savedProduct);
    }

    // readOnly = true → tells Hibernate this is a read-only transaction:
    // - Keeps the session open so LAZY collections (images, categories) can be loaded
    // - Skips dirty-checking on flush → better performance for reads
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProducts(int page, int size, String sortBy, String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();

        log.info("Fetching jewellery products page={} size={} sortBy={} sortDir={}", page, size, sortBy, sortDir);

        Pageable pageable = PageRequest.of(page, size, sort);
        return productRepository.findAll(pageable).map(this::mapToProductResponse);
    }

    @Cacheable(
            value = "product",
            key = "#productId"
    )
    @Transactional(readOnly = true)
    public ProductResponse getSingleProduct(Long productId) {
        return mapToProductResponse(findProductById(productId));
    }

    @Transactional
    @CacheEvict(
            value = "product",
            key = "#productId"
    )
    public void deleteProductById(Long productId) {
        Product product = findProductById(productId);

        log.info("Deleting jewellery product id={} title={}", productId, product.getTitle());

        // Remove stock from inventory-service first
        // TODO: handle partial failure — consider SAGA / outbox pattern
        try {
            inventoryServiceClient.deleteInventory(productId);
        } catch (FeignException e) {
            log.error("Inventory service error while deleting product id={}: status={} message={}",
                    productId, e.status(), e.getMessage());
            throw new CustomException("Failed to delete inventory record — inventory service returned an error");
        } catch (Exception e) {
            log.error("Failed to reach inventory service while deleting product id={}: {}", productId, e.getMessage());
            throw new CustomException("Failed to delete inventory record — inventory service unreachable");
        }

        try {
            productRepository.deleteById(productId);
        } catch (Exception e) {
            log.error("Unexpected error while deleting product id={}: {}", productId, e.getMessage());
            throw new CustomException("Failed to delete product: " + e.getMessage());
        }
    }

    @Transactional
    @CachePut(
            value = "product",
            key = "#productId"
    )
    public ProductResponse updateProduct(Long productId, ProductRequest request) {
        Product product = findProductById(productId);

        product.setTitle(request.getTitle());
        product.setDescription(request.getDescription());
        product.setDiamond(request.getDiamond());
        product.setMaterial(request.getMaterial());
        product.setBasePrice(request.getBasePrice());
        product.setDiscountedPrice(request.getDiscountedPrice());
        product.setCategories(validateAndFetchCategories(request.getCategories()));

        syncImages(product, request.getImages());

        Product savedProduct;
        try {
            savedProduct = productRepository.save(product);
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation while updating product id={}: {}", productId, e.getMostSpecificCause().getMessage());
            throw new BadRequestException("Update violates a uniqueness constraint");
        } catch (Exception e) {
            log.error("Unexpected error while updating product id={}: {}", productId, e.getMessage());
            throw new CustomException("Failed to update product: " + e.getMessage());
        }

        log.info("Updated jewellery product id={}", productId);

        // Sync updated quantity in inventory-service
        // TODO: handle partial failure — consider SAGA / outbox pattern
        try {
            inventoryServiceClient.syncInventory(
                    InventoryRequest.builder()
                            .productId(savedProduct.getId())
                            .availableQuantity(request.getQuantity())
                            .build()
            );
        } catch (FeignException e) {
            log.error("Inventory service error while syncing product id={}: status={} message={}",
                    productId, e.status(), e.getMessage());
            throw new CustomException("Product update failed — inventory service returned an error (transaction rolled back)");
        } catch (Exception e) {
            log.error("Failed to reach inventory service for product id={}: {}", productId, e.getMessage());
            throw new CustomException("Product update failed — inventory service unreachable (transaction rolled back)");
        }

        return mapToProductResponse(savedProduct);
    }

    // ── Helpers — Product ──────────────────────────────────────────────────────

    private Product findProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
    }

    private Set<Category> validateAndFetchCategories(Collection<ProductCategoryDto> categoryDtos) {
        if (categoryDtos == null || categoryDtos.isEmpty()) {
            throw new BadRequestException("Category cannot be empty");
        }

        Set<Long> categoryIds = categoryDtos.stream()
                .map(ProductCategoryDto::getId)
                .collect(Collectors.toSet());

        Set<Category> categories = new HashSet<>(categoryRepository.findAllById(categoryIds));

        if (categories.size() != categoryDtos.size()) {
            throw new BadRequestException("Some of the provided categories are invalid");
        }

        return categories;
    }

    private void validateImageUrls(Collection<ProductImageDto> dtos) {
        dtos.forEach(dto -> {
            if (dto.getImageUrl() == null || dto.getImageUrl().isBlank()) {
                throw new BadRequestException("Image URL cannot be empty");
            }
        });
    }

    // ── Helpers — Images ───────────────────────────────────────────────────────

    /**
     * Builds ProductImage entities with the product reference set on the OWNING side.
     * ProductImage.product (the @ManyToOne @JoinColumn side) is what Hibernate uses
     * to determine the product_id FK value — it MUST be set, not just Product.images.
     * This method is always called with a savedProduct (has a real ID) to avoid null product_id.
     */
    private Set<ProductImage> buildNewImages(Collection<ProductImageDto> dtos, Product product) {
        return dtos.stream()
                .map(i -> ProductImage.builder()
                        .imageUrl(i.getImageUrl())
                        .thumbnail(i.getThumbnail())
                        .product(product)   // ← owning side set → product_id is populated correctly
                        .build())
                .collect(Collectors.toSet());
    }

    private void syncImages(Product product, Set<ProductImageDto> incoming) {
        if (incoming == null) return;

        Map<Long, ProductImage> existingMap = product.getImages().stream()
                .collect(Collectors.toMap(ProductImage::getId, i -> i));

        Set<Long> incomingIds = new HashSet<>();
        List<ProductImage> newImages = new ArrayList<>();

        for (ProductImageDto dto : incoming) {
            if (dto.getId() != null && existingMap.containsKey(dto.getId())) {
                ProductImage existing = existingMap.get(dto.getId());
                existing.setImageUrl(dto.getImageUrl());
                existing.setThumbnail(dto.getThumbnail());
                incomingIds.add(dto.getId());
            } else {
                newImages.add(ProductImage.builder()
                        .imageUrl(dto.getImageUrl())
                        .thumbnail(dto.getThumbnail())
                        .product(product)
                        .build());
            }
        }

        product.getImages().removeIf(i -> i.getId() != null && !incomingIds.contains(i.getId()));
        product.getImages().addAll(newImages);
    }

    // ── Helpers — Mapping ──────────────────────────────────────────────────────

    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .title(product.getTitle())
                .description(product.getDescription())
                .diamond(product.getDiamond())
                .materialType(product.getMaterial())
                .basePrice(product.getBasePrice())
                .discountedPrice(product.getDiscountedPrice())
                .categories(mapToCategoryDtos(product.getCategories()))
                .images(mapToImageDtos(product.getImages()))
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }

    private Set<ProductCategoryDto> mapToCategoryDtos(Set<Category> categories) {
        return categories.stream()
                .map(c -> ProductCategoryDto.builder().id(c.getId()).name(c.getName()).build())
                .collect(Collectors.toSet());
    }

    private Set<ProductImageDto> mapToImageDtos(Set<ProductImage> images) {
        return images.stream()
                .map(i -> ProductImageDto.builder()
                        .id(i.getId()).imageUrl(i.getImageUrl()).thumbnail(i.getThumbnail()).build())
                .collect(Collectors.toSet());
    }
}
