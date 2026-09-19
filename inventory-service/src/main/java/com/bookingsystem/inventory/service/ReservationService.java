package com.bookingsystem.inventory.service;

import com.bookingsystem.common.dto.ReservationRequest;
import com.bookingsystem.common.dto.ReservationResponse;
import com.bookingsystem.inventory.domain.InventoryItem;
import com.bookingsystem.inventory.domain.Reservation;
import com.bookingsystem.inventory.exception.LockAcquisitionTimeoutException;
import com.bookingsystem.inventory.repository.ReservationRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Core concurrency-critical path: reserving units of an {@link InventoryItem}.
 *
 * Correctness relies on three mechanisms, from primary to backstop:
 * 1. Distributed lock (Redisson {@link RLock}, keyed per inventory item):
 *    only one thread across the whole system can be inside the reservation
 *    critical section for a given item at a time. This is what actually
 *    prevents contention -- at high concurrency (tested with 100 parallel
 *    requests against a single 10-unit item), letting every request race
 *    directly against MySQL row locks produced genuine InnoDB deadlocks
 *    (a single-row UPDATE plus a child INSERT with a FK to that row is a
 *    known InnoDB deadlock pattern under heavy contention), not just
 *    optimistic-lock version conflicts. Serializing at the application
 *    layer with a cheap Redis lock avoids that entirely.
 * 2. Idempotency: the idempotency key has a unique DB constraint (see
 *    {@link Reservation}), so a retried request finds the existing row
 *    instead of reserving twice, independent of the lock.
 * 3. Optimistic locking ({@link InventoryItem#getVersion()}, a JPA
 *    {@code @Version} column) stays on the entity as a defense-in-depth
 *    safety net -- e.g. if the lock is ever bypassed, misconfigured, or
 *    expires mid-operation, a lost update still fails loudly instead of
 *    silently corrupting inventory counts.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);
    private static final long LOCK_WAIT_SECONDS = 5;
    private static final long LOCK_LEASE_SECONDS = 10;

    private final ReservationRepository reservationRepository;
    private final ReservationTransactionalOps transactionalOps;
    private final RedissonClient redissonClient;

    public ReservationService(ReservationRepository reservationRepository,
                               ReservationTransactionalOps transactionalOps,
                               RedissonClient redissonClient) {
        this.reservationRepository = reservationRepository;
        this.transactionalOps = transactionalOps;
        this.redissonClient = redissonClient;
    }

    public ReservationResponse reserve(ReservationRequest request) {
        return reservationRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(transactionalOps::toResponse)
                .orElseGet(() -> reserveWithLock(request));
    }

    private ReservationResponse reserveWithLock(ReservationRequest request) {
        RLock lock = redissonClient.getLock("inventory-item-lock:" + request.inventoryItemId());
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for inventory lock", e);
        }

        if (!acquired) {
            throw new LockAcquisitionTimeoutException(request.inventoryItemId());
        }

        try {
            return doReserveWithIdempotencyFallback(request);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private ReservationResponse doReserveWithIdempotencyFallback(ReservationRequest request) {
        try {
            return transactionalOps.doReserve(request);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Defense-in-depth path: shouldn't happen while the distributed
            // lock is held correctly, but if it does, one retry against a
            // freshly-read row is safe and cheap.
            log.warn("Unexpected optimistic lock conflict on item {} despite holding distributed lock, retrying once",
                    request.inventoryItemId());
            return transactionalOps.doReserve(request);
        } catch (DataIntegrityViolationException e) {
            // Lost the race on the idempotency unique constraint: another
            // thread inserted the same key between our check and our insert.
            log.info("Idempotency key {} already reserved concurrently, returning existing result",
                    request.idempotencyKey());
            return reservationRepository.findByIdempotencyKey(request.idempotencyKey())
                    .map(transactionalOps::toResponse)
                    .orElseThrow(() -> e);
        }
    }
}
