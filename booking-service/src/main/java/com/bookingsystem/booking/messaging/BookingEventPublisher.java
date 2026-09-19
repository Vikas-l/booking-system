package com.bookingsystem.booking.messaging;

import com.bookingsystem.common.dto.BookingConfirmedEvent;
import com.bookingsystem.common.dto.BookingFailedEvent;
import com.bookingsystem.common.dto.BookingReservedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class BookingEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(BookingEventPublisher.class);

    public static final String TOPIC_BOOKING_RESERVED = "booking.reserved";
    public static final String TOPIC_BOOKING_CONFIRMED = "booking.confirmed";
    public static final String TOPIC_BOOKING_FAILED = "booking.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public BookingEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReserved(BookingReservedEvent event) {
        log.info("Publishing {} for booking {}", TOPIC_BOOKING_RESERVED, event.bookingId());
        kafkaTemplate.send(TOPIC_BOOKING_RESERVED, event.bookingId().toString(), event);
    }

    public void publishConfirmed(BookingConfirmedEvent event) {
        log.info("Publishing {} for booking {}", TOPIC_BOOKING_CONFIRMED, event.bookingId());
        kafkaTemplate.send(TOPIC_BOOKING_CONFIRMED, event.bookingId().toString(), event);
    }

    public void publishFailed(BookingFailedEvent event) {
        log.info("Publishing {} for booking {}", TOPIC_BOOKING_FAILED, event.bookingId());
        kafkaTemplate.send(TOPIC_BOOKING_FAILED, event.bookingId().toString(), event);
    }
}
