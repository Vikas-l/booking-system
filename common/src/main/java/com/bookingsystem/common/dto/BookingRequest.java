package com.bookingsystem.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BookingRequest(
        @NotNull Long inventoryItemId,
        @Min(1) int quantity,
        @NotBlank String idempotencyKey
) {
}
