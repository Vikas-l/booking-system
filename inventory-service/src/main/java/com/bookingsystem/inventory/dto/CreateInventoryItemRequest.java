package com.bookingsystem.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateInventoryItemRequest(
        @NotBlank String name,
        @Min(1) int totalQuantity
) {
}
