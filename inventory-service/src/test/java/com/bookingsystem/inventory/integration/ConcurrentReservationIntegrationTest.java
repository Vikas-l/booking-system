package com.bookingsystem.inventory.integration;

import com.bookingsystem.common.dto.ReservationRequest;
import com.bookingsystem.common.dto.ReservationResponse;
import com.bookingsystem.inventory.domain.InventoryItem;
import com.bookingsystem.inventory.exception.InsufficientInventoryException;
import com.bookingsystem.inventory.repository.InventoryItemRepository;
import com.bookingsystem.inventory.repository.ReservationRepository;
import com.bookingsystem.inventory.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the core correctness claim of the project: N seats, more than N
 * concurrent requests each for 1 seat, and the system reserves exactly N
 * -- no overselling, no lost updates -- via optimistic-locking retries.
 */
class ConcurrentReservationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void concurrentReservationsNeverOversellInventory() throws InterruptedException {
        int totalSeats = 10;
        int concurrentRequests = 100;

        InventoryItem item = inventoryItemRepository.save(new InventoryItem("Concert Seat", totalSeats));

        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        List<Callable<Void>> tasks = IntStream.range(0, concurrentRequests)
                .<Callable<Void>>mapToObj(i -> () -> {
                    ReservationRequest request = new ReservationRequest(item.getId(), 1, UUID.randomUUID().toString());
                    try {
                        reservationService.reserve(request);
                        succeeded.incrementAndGet();
                    } catch (InsufficientInventoryException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                })
                .toList();

        List<Future<Void>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        // surface any unexpected exception instead of letting it hide inside the Future
        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        InventoryItem reloaded = inventoryItemRepository.findById(item.getId()).orElseThrow();

        assertThat(succeeded.get()).isEqualTo(totalSeats);
        assertThat(rejected.get()).isEqualTo(concurrentRequests - totalSeats);
        assertThat(reloaded.getReservedQuantity()).isEqualTo(totalSeats);
        assertThat(reloaded.availableQuantity()).isZero();
        assertThat(reservationRepository.countByInventoryItemId(item.getId())).isEqualTo(totalSeats);
    }

    @Test
    void duplicateIdempotencyKeyDoesNotDoubleReserve() throws InterruptedException {
        InventoryItem item = inventoryItemRepository.save(new InventoryItem("Movie Ticket", 5));
        String idempotencyKey = UUID.randomUUID().toString();

        int concurrentRetries = 10;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRetries);

        List<Callable<ReservationResponse>> tasks = IntStream.range(0, concurrentRetries)
                .<Callable<ReservationResponse>>mapToObj(i -> () ->
                        reservationService.reserve(new ReservationRequest(item.getId(), 1, idempotencyKey)))
                .toList();

        List<Future<ReservationResponse>> futures = executor.invokeAll(tasks);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);

        List<Long> distinctReservationIds = futures.stream()
                .map(f -> {
                    try {
                        return f.get().reservationId();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .distinct()
                .toList();

        InventoryItem reloaded = inventoryItemRepository.findById(item.getId()).orElseThrow();

        assertThat(distinctReservationIds).hasSize(1);
        assertThat(reloaded.getReservedQuantity()).isEqualTo(1);
        assertThat(reservationRepository.countByInventoryItemId(item.getId())).isEqualTo(1);
    }
}
