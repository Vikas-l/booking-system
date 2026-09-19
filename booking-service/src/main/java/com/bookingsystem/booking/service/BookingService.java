package com.bookingsystem.booking.service;

import com.bookingsystem.booking.client.InventoryClient;
import com.bookingsystem.booking.domain.Booking;
import com.bookingsystem.booking.domain.BookingStatus;
import com.bookingsystem.booking.exception.BookingNotFoundException;
import com.bookingsystem.booking.exception.InventoryReservationFailedException;
import com.bookingsystem.booking.messaging.BookingEventPublisher;
import com.bookingsystem.booking.repository.BookingRepository;
import com.bookingsystem.common.dto.BookingConfirmedEvent;
import com.bookingsystem.common.dto.BookingFailedEvent;
import com.bookingsystem.common.dto.BookingRequest;
import com.bookingsystem.common.dto.BookingReservedEvent;
import com.bookingsystem.common.dto.BookingResponse;
import com.bookingsystem.common.dto.PaymentCompletedEvent;
import com.bookingsystem.common.dto.PaymentFailedEvent;
import com.bookingsystem.common.dto.ReservationRequest;
import com.bookingsystem.common.dto.ReservationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the booking saga: reserve (sync REST call to Inventory
 * Service) -> publish for async payment -> react to the payment outcome by
 * confirming or compensating (releasing the reservation).
 *
 * The reserve step is synchronous because the client needs an immediate
 * answer to "is this even possible" (no point accepting a booking for an
 * item that's already sold out). Everything after that is async via Kafka,
 * because payment is the kind of step that can be slow/retried/fail
 * independently, and using Kafka's durable log instead of a direct call
 * means a crashed Payment Service doesn't lose the request -- it's replayed
 * on restart from the last committed offset.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final InventoryClient inventoryClient;
    private final BookingEventPublisher eventPublisher;

    public BookingService(BookingRepository bookingRepository,
                           InventoryClient inventoryClient,
                           BookingEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.inventoryClient = inventoryClient;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        return bookingRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(this::toResponse)
                .orElseGet(() -> reserveAndPublish(request));
    }

    private BookingResponse reserveAndPublish(BookingRequest request) {
        Booking booking = new Booking(request.inventoryItemId(), request.quantity(), request.idempotencyKey());
        booking = bookingRepository.save(booking);

        try {
            ReservationResponse reservation = inventoryClient.reserve(
                    new ReservationRequest(request.inventoryItemId(), request.quantity(), request.idempotencyKey()));
            booking.markReserved(reservation.reservationId());
            booking = bookingRepository.save(booking);

            eventPublisher.publishReserved(new BookingReservedEvent(
                    booking.getId(), booking.getReservationId(), booking.getInventoryItemId(),
                    booking.getQuantity(), booking.getIdempotencyKey()));
        } catch (InventoryReservationFailedException e) {
            log.info("Booking {} failed at reservation step: {}", booking.getId(), e.getMessage());
            booking.markFailed(e.getMessage());
            booking = bookingRepository.save(booking);
        }

        return toResponse(booking);
    }

    @Transactional
    public void confirmBooking(PaymentCompletedEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(event.bookingId()));

        if (booking.getStatus() != BookingStatus.RESERVED) {
            log.info("Booking {} already in status {}, ignoring duplicate payment.completed",
                    booking.getId(), booking.getStatus());
            return;
        }

        booking.markConfirmed();
        bookingRepository.save(booking);

        eventPublisher.publishConfirmed(new BookingConfirmedEvent(
                booking.getId(), booking.getInventoryItemId(), booking.getQuantity()));
    }

    @Transactional
    public void cancelBooking(PaymentFailedEvent event) {
        Booking booking = bookingRepository.findById(event.bookingId())
                .orElseThrow(() -> new BookingNotFoundException(event.bookingId()));

        if (booking.getStatus() != BookingStatus.RESERVED) {
            log.info("Booking {} already in status {}, ignoring duplicate payment.failed",
                    booking.getId(), booking.getStatus());
            return;
        }

        // Compensating action: release the reservation before flipping the
        // booking to CANCELLED, so a crash between these two steps leaves
        // the booking as RESERVED (safe to reprocess this same event again)
        // rather than CANCELLED with inventory never released.
        inventoryClient.release(booking.getReservationId());

        booking.markCancelled(event.reason());
        bookingRepository.save(booking);

        eventPublisher.publishFailed(new BookingFailedEvent(
                booking.getId(), booking.getInventoryItemId(), booking.getQuantity(), event.reason()));
    }

    public BookingResponse getBooking(Long id) {
        return bookingRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new BookingNotFoundException(id));
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getInventoryItemId(),
                booking.getReservationId(),
                booking.getQuantity(),
                booking.getStatus().name(),
                booking.getFailureReason(),
                booking.getIdempotencyKey()
        );
    }
}
