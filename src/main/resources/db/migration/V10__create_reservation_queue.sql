CREATE TABLE reservation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reader_id BIGINT NOT NULL,
    book_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    active_reader_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'PENDING' THEN reader_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uk_reservation_active_reader_book UNIQUE (book_id, active_reader_id),
    CONSTRAINT fk_reservation_reader FOREIGN KEY (reader_id) REFERENCES reader_profile (id) ON DELETE RESTRICT,
    CONSTRAINT fk_reservation_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE RESTRICT,
    CONSTRAINT ck_reservation_status CHECK (status IN ('PENDING', 'CANCELLED')),
    CONSTRAINT ck_reservation_cancelled_at CHECK (
        (status = 'PENDING' AND cancelled_at IS NULL) OR
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
    ),
    CONSTRAINT ck_reservation_version CHECK (version >= 0),
    INDEX ix_reservation_queue (book_id, status, created_at, id),
    INDEX ix_reservation_reader (reader_id, created_at, id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
