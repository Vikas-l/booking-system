package com.bookingsystem.common.dto;

/**
 * Published by Booking Service after it successfully reserves inventory,
 * to kick off the async payment step of the saga.
 */
public record BookingReservedEvent(
        Long bookingId,
        Long reservationId,
        Long inventoryItemId,
        int quantity,
        String idempotencyKey
) {
}
