ALTER TABLE product
    ADD COLUMN version INT NOT NULL DEFAULT 0;

ALTER TABLE orders
    ADD COLUMN version INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS payment_callback_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    callback_id VARCHAR(96) NOT NULL UNIQUE,
    order_id BIGINT NOT NULL,
    payload VARCHAR(1000),
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_payment_callback_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
