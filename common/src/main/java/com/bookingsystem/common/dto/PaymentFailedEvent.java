package com.bookingsystem.common.dto;

/**
 * Published by Payment Service when the simulated payment for a booking
 * fails. Consumed by Booking Service to trigger the compensating action
 * (release the reservation) and move the booking to CANCELLED.
 */
public record PaymentFailedEvent(
        Long bookingId,
        Long reservationId,
        Long inventoryItemId,
        int quantity,
        String reason
) {
}
