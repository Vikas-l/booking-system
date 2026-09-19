package com.bookingsystem.inventory.exception;

public class InsufficientInventoryException extends RuntimeException {

    public InsufficientInventoryException(Long inventoryItemId, int requested, int available) {
        super("Cannot reserve %d unit(s) of item %d: only %d available"
                .formatted(requested, inventoryItemId, available));
    }
}
