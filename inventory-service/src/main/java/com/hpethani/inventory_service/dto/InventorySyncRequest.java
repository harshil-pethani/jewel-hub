package com.hpethani.inventory_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventorySyncRequest {
    private List<InventoryRequest> inventoryRequests;
    private Set<Long> deletedVariantIds;
}
