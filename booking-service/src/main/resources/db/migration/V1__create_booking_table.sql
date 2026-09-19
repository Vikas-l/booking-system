CREATE TABLE booking (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_item_id BIGINT          NOT NULL,
    reservation_id    BIGINT,
    quantity          INT             NOT NULL,
    idempotency_key   VARCHAR(255)    NOT NULL,
    status            VARCHAR(20)     NOT NULL,
    failure_reason    VARCHAR(500),
    created_at        TIMESTAMP(6)    NOT NULL,
    updated_at        TIMESTAMP(6)    NOT NULL,
    CONSTRAINT uq_booking_idempotency_key UNIQUE (idempotency_key)
) ENGINE=InnoDB;

CREATE INDEX idx_booking_reservation_id ON booking (reservation_id);
