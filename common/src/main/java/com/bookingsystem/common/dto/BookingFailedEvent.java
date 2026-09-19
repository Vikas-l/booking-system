package com.bookingsystem.common.dto;

/**
 * Published by Booking Service once a booking is cancelled (payment
 * failed, reservation released). Consumed by Notification Service to
 * simulate a failure message.
 */
public record BookingFailedEvent(
        Long bookingId,
        Long inventoryItemId,
        int quantity,
        String reason
) {
}
