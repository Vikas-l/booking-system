package com.bookingsystem.booking.service;

import com.bookingsystem.booking.domain.Booking;
import com.bookingsystem.booking.domain.BookingStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookingDomainTest {

    @Test
    void newBookingStartsPending() {
        Booking booking = new Booking(1L, 2, "key-1");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(booking.getReservationId()).isNull();
    }

    @Test
    void markReservedSetsReservationIdAndStatus() {
        Booking booking = new Booking(1L, 2, "key-1");

        booking.markReserved(99L);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.RESERVED);
        assertThat(booking.getReservationId()).isEqualTo(99L);
    }

    @Test
    void markConfirmedTransitionsFromReserved() {
        Booking booking = new Booking(1L, 2, "key-1");
        booking.markReserved(99L);

        booking.markConfirmed();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void markCancelledRecordsReason() {
        Booking booking = new Booking(1L, 2, "key-1");
        booking.markReserved(99L);

        booking.markCancelled("payment declined");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getFailureReason()).isEqualTo("payment declined");
    }

    @Test
    void markFailedRecordsReasonWithoutReservation() {
        Booking booking = new Booking(1L, 2, "key-1");

        booking.markFailed("insufficient inventory");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.FAILED);
        assertThat(booking.getFailureReason()).isEqualTo("insufficient inventory");
        assertThat(booking.getReservationId()).isNull();
    }
}
