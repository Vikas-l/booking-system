package com.bookingsystem.inventory.dto;

import com.bookingsystem.inventory.domain.InventoryItem;

public record InventoryItemResponse(
        Long id,
        String name,
        int totalQuantity,
        int reservedQuantity,
        int availableQuantity
) {
    public static InventoryItemResponse from(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(),
                item.getName(),
                item.getTotalQuantity(),
                item.getReservedQuantity(),
                item.availableQuantity()
        );
    }
}
