package com.bookingsystem.payment.messaging;

import com.bookingsystem.common.dto.BookingReservedEvent;
import com.bookingsystem.common.dto.PaymentCompletedEvent;
import com.bookingsystem.common.dto.PaymentFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Simulates payment processing. Real payment gateways aren't in scope for
 * this project, so the "processing" is a deterministic rule rather than an
 * actual charge -- deterministic (not random) so the saga's compensation
 * path is reliably reproducible for tests and demos rather than flaky.
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);
    private static final int FAILURE_QUANTITY_THRESHOLD = 4;

    public static final String TOPIC_PAYMENT_COMPLETED = "payment.completed";
    public static final String TOPIC_PAYMENT_FAILED = "payment.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventListener(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "booking.reserved", groupId = "payment-service")
    public void onBookingReserved(BookingReservedEvent event) {
        log.info("Processing payment for booking {} (quantity={})", event.bookingId(), event.quantity());

        String key = event.bookingId().toString();
        if (event.quantity() >= FAILURE_QUANTITY_THRESHOLD) {
            String reason = "Simulated payment decline: quantity %d >= threshold %d"
                    .formatted(event.quantity(), FAILURE_QUANTITY_THRESHOLD);
            log.info("Payment failed for booking {}: {}", event.bookingId(), reason);
            kafkaTemplate.send(TOPIC_PAYMENT_FAILED, key, new PaymentFailedEvent(
                    event.bookingId(), event.reservationId(), event.inventoryItemId(), event.quantity(), reason));
        } else {
            log.info("Payment completed for booking {}", event.bookingId());
            kafkaTemplate.send(TOPIC_PAYMENT_COMPLETED, key, new PaymentCompletedEvent(
                    event.bookingId(), event.reservationId(), event.inventoryItemId(), event.quantity()));
        }
    }
}
