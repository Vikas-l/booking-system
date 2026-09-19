package com.bookingsystem.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.bookingsystem.inventory.exception.InsufficientInventoryException;

/**
 * A pool of identical bookable units (e.g. seats for one showtime).
 * {@code version} backs JPA optimistic locking: concurrent reservations
 * against the same row race on this column, and the loser retries instead
 * of silently overwriting the winner's update (lost-update prevention).
 */
@Entity
@Table(name = "inventory_item")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version // JPA optimistic locking Hibernate automatically adds AND version = ? to every UPDATE and bumps it by 1 on save. If two transactions load the same row and both try to update it, the second one's UPDATE matches zero rows (because the version already changed), and Hibernate throws ObjectOptimisticLockingFailureException.
    @Column(nullable = false)
    private long version;

    protected InventoryItem() {
        // JPA
    }

    public InventoryItem(String name, int totalQuantity) {
        this.name = name;
        this.totalQuantity = totalQuantity;
        this.reservedQuantity = 0;
    }

    public int availableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    public void reserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (availableQuantity() < quantity) {
            throw new InsufficientInventoryException(id, quantity, availableQuantity());
        }
        this.reservedQuantity += quantity;
    }

    public void release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        this.reservedQuantity = Math.max(0, this.reservedQuantity - quantity);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public long getVersion() {
        return version;
    }
}
