package com.bookingsystem.common.dto;

/**
 * Published by Booking Service once a booking is fully confirmed (payment
 * succeeded). Consumed by Notification Service to simulate a confirmation
 * message.
 */
public record BookingConfirmedEvent(
        Long bookingId,
        Long inventoryItemId,
        int quantity
) {
}
