package com.bookingsystem.common.dto;

public record BookingResponse(
        Long bookingId,
        Long inventoryItemId,
        Long reservationId,
        int quantity,
        String status,
        String failureReason,
        String idempotencyKey
) {
}
