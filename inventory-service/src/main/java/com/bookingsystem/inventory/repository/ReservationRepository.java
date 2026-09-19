package com.bookingsystem.inventory.repository;

import com.bookingsystem.inventory.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    long countByInventoryItemId(Long inventoryItemId);
}
