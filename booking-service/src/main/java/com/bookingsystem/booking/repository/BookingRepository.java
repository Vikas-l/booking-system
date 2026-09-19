package com.bookingsystem.booking.repository;

import com.bookingsystem.booking.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    Optional<Booking> findByReservationId(Long reservationId);
}
