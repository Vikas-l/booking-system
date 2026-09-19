package com.bookingsystem.inventory.exception;

public class LockAcquisitionTimeoutException extends RuntimeException {

    public LockAcquisitionTimeoutException(Long inventoryItemId) {
        super("Timed out waiting for reservation lock on inventory item " + inventoryItemId);
    }
}
