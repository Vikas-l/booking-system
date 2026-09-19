package com.bookingsystem.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Tracks one booking through the reserve -> pay -> confirm/cancel saga.
 * {@code reservationId} is null until the synchronous reserve call to
 * Inventory Service succeeds; {@code status} then advances as Kafka events
 * for the payment outcome arrive.
 */
@Entity
@Table(name = "booking", uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"))
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_item_id", nullable = false)
    private Long inventoryItemId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Booking() {
        // JPA
    }

    public Booking(Long inventoryItemId, int quantity, String idempotencyKey) {
        this.inventoryItemId = inventoryItemId;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
        this.status = BookingStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markReserved(Long reservationId) {
        this.reservationId = reservationId;
        this.status = BookingStatus.RESERVED;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = BookingStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void markConfirmed() {
        this.status = BookingStatus.CONFIRMED;
        this.updatedAt = Instant.now();
    }

    public void markCancelled(String reason) {
        this.status = BookingStatus.CANCELLED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getInventoryItemId() {
        return inventoryItemId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
