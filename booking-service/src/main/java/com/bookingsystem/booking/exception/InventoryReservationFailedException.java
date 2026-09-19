package com.bookingsystem.booking.exception;

/**
 * Raised when the Inventory Service rejects a reservation request (e.g.
 * insufficient stock, HTTP 409). Distinguished from a transport/technical
 * failure (5xx, timeout) so {@link com.bookingsystem.booking.service.BookingService}
 * can mark the booking FAILED immediately instead of leaving it PENDING for
 * a retry that would never succeed.
 */
public class InventoryReservationFailedException extends RuntimeException {

    public InventoryReservationFailedException(String message) {
        super(message);
    }
}
