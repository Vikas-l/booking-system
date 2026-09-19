package com.bookingsystem.common.dto;

/**
 * Published by Payment Service when the simulated payment for a booking
 * succeeds. Consumed by Booking Service to move the booking to CONFIRMED.
 */
public record PaymentCompletedEvent(
        Long bookingId,
        Long reservationId,
        Long inventoryItemId,
        int quantity
) {
}
