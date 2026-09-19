CREATE TABLE inventory_item (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    name              VARCHAR(255)    NOT NULL,
    total_quantity    INT             NOT NULL,
    reserved_quantity INT             NOT NULL DEFAULT 0,
    version           BIGINT          NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE reservation (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventory_item_id BIGINT          NOT NULL,
    quantity          INT             NOT NULL,
    idempotency_key   VARCHAR(255)    NOT NULL,
    status            VARCHAR(20)     NOT NULL,
    created_at        TIMESTAMP(6)    NOT NULL,
    CONSTRAINT uq_reservation_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_reservation_inventory_item FOREIGN KEY (inventory_item_id)
        REFERENCES inventory_item (id)
) ENGINE=InnoDB;

CREATE INDEX idx_reservation_inventory_item_id ON reservation (inventory_item_id);
