package com.bookingsystem.inventory.service;

import com.bookingsystem.common.dto.ReservationRequest;
import com.bookingsystem.common.dto.ReservationResponse;
import com.bookingsystem.inventory.domain.InventoryItem;
import com.bookingsystem.inventory.domain.Reservation;
import com.bookingsystem.inventory.domain.ReservationStatus;
import com.bookingsystem.inventory.exception.InventoryItemNotFoundException;
import com.bookingsystem.inventory.repository.InventoryItemRepository;
import com.bookingsystem.inventory.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolated as its own Spring bean (rather than a package-private method on
 * {@link ReservationService}) so that {@code @Transactional(REQUIRES_NEW)}
 * goes through the Spring AOP proxy. A self-invoked method call (this.foo())
 * bypasses the proxy entirely and the transactional annotation on it would
 * be silently ignored.
 *
 * Callers are expected to hold the per-item Redisson distributed lock (see
 * {@link ReservationService}) before invoking {@link #doReserve}, so this
 * method itself only ever executes for one thread per inventory item at a
 * time. {@code isolation = READ_COMMITTED} and the insert-before-update
 * ordering below are extra hardening against InnoDB deadlocks (single-row
 * UPDATE plus a child INSERT with a FK to that row is a known deadlock
 * pattern under heavy contention) in case that lock is ever bypassed --
 * not the primary correctness mechanism.
 */
@Component
public class ReservationTransactionalOps {

    private static final Logger log = LoggerFactory.getLogger(ReservationTransactionalOps.class);

    private final InventoryItemRepository inventoryItemRepository;
    private final ReservationRepository reservationRepository;

    public ReservationTransactionalOps(InventoryItemRepository inventoryItemRepository,
                                        ReservationRepository reservationRepository) {
        this.inventoryItemRepository = inventoryItemRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public ReservationResponse doReserve(ReservationRequest request) {
        InventoryItem item = inventoryItemRepository.findById(request.inventoryItemId())
                .orElseThrow(() -> new InventoryItemNotFoundException(request.inventoryItemId()));

        item.reserve(request.quantity());

        Reservation reservation = new Reservation(
                item.getId(), request.quantity(), request.idempotencyKey(), ReservationStatus.RESERVED);
        reservation = reservationRepository.saveAndFlush(reservation);

        inventoryItemRepository.save(item);

        log.info("Reserved {} unit(s) of item {} (reservationId={}, idempotencyKey={})",
                request.quantity(), item.getId(), reservation.getId(), request.idempotencyKey());

        return toResponse(reservation);
    }

    ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getInventoryItemId(),
                reservation.getQuantity(),
                reservation.getStatus().name(),
                reservation.getIdempotencyKey()
        );
    }
}
