package com.bookingsystem.inventory.domain;

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
 * Records the outcome of a reservation attempt, keyed on the caller-supplied
 * idempotency key. A unique constraint on that column is what makes retried
 * requests (client timeout + retry, at-least-once Kafka redelivery, etc.)
 * safe: the second attempt finds the first row instead of reserving twice.
 */
@Entity
@Table(name = "reservation", uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"))
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inventory_item_id", nullable = false)
    private Long inventoryItemId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Reservation() {
        // JPA
    }

    public Reservation(Long inventoryItemId, int quantity, String idempotencyKey, ReservationStatus status) {
        this.inventoryItemId = inventoryItemId;
        this.quantity = quantity;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getInventoryItemId() {
        return inventoryItemId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
