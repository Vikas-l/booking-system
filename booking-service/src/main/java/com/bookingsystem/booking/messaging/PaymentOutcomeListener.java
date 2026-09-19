package com.bookingsystem.booking.messaging;

import com.bookingsystem.common.dto.PaymentCompletedEvent;
import com.bookingsystem.common.dto.PaymentFailedEvent;
import com.bookingsystem.booking.service.BookingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes the payment outcome topics and drives the booking to its final
 * state. Each handler is idempotent at the {@link BookingService} level
 * (keyed off the booking's current status), so at-least-once Kafka
 * redelivery is safe -- reprocessing the same event twice is a no-op.
 */
@Component
public class PaymentOutcomeListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutcomeListener.class);

    private final BookingService bookingService;

    public PaymentOutcomeListener(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @KafkaListener(topics = "payment.completed", groupId = "booking-service")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received payment.completed for booking {}", event.bookingId());
        bookingService.confirmBooking(event);
    }

    @KafkaListener(topics = "payment.failed", groupId = "booking-service")
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received payment.failed for booking {}: {}", event.bookingId(), event.reason());
        bookingService.cancelBooking(event);
    }
}
