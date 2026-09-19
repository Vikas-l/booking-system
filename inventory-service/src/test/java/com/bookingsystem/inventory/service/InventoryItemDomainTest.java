package com.bookingsystem.inventory.service;

import com.bookingsystem.inventory.domain.InventoryItem;
import com.bookingsystem.inventory.exception.InsufficientInventoryException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemDomainTest {

    @Test
    void reserveReducesAvailableQuantity() {
        InventoryItem item = new InventoryItem("Seat", 10);

        item.reserve(3);

        assertThat(item.getReservedQuantity()).isEqualTo(3);
        assertThat(item.availableQuantity()).isEqualTo(7);
    }

    @Test
    void reserveMoreThanAvailableThrows() {
        InventoryItem item = new InventoryItem("Seat", 5);
        item.reserve(5);

        assertThatThrownBy(() -> item.reserve(1))
                .isInstanceOf(InsufficientInventoryException.class);
    }

    @Test
    void releaseNeverGoesBelowZero() {
        InventoryItem item = new InventoryItem("Seat", 5);
        item.reserve(2);

        item.release(10);

        assertThat(item.getReservedQuantity()).isZero();
        assertThat(item.availableQuantity()).isEqualTo(5);
    }
}
