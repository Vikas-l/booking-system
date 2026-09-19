package com.bookingsystem.common.dto;

public record ReservationResponse(
        Long reservationId,
        Long inventoryItemId,
        int quantity,
        String status,
        String idempotencyKey
) {
}
