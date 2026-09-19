package com.bookingsystem.notification.messaging;

import com.bookingsystem.common.dto.BookingConfirmedEvent;
import com.bookingsystem.common.dto.BookingFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Simulates sending a customer notification. No real email/SMS provider
 * is in scope -- the point is demonstrating the fan-out: both this service
 * and Booking Service's own state independently react to the same saga
 * outcome events, decoupled from each other via Kafka.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    @KafkaListener(topics = "booking.confirmed", groupId = "notification-service")
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Notification: booking {} confirmed for item {}, quantity {}",
                event.bookingId(), event.inventoryItemId(), event.quantity());
    }

    @KafkaListener(topics = "booking.failed", groupId = "notification-service")
    public void onBookingFailed(BookingFailedEvent event) {
        log.info("Notification: booking {} failed for item {}, quantity {} -- reason: {}",
                event.bookingId(), event.inventoryItemId(), event.quantity(), event.reason());
    }
}
