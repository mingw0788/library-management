CREATE TABLE book_copy (
    id BIGINT NOT NULL AUTO_INCREMENT,
    book_id BIGINT NOT NULL,
    barcode VARCHAR(50) NOT NULL,
    shelf_location VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_book_copy_barcode UNIQUE (barcode),
    CONSTRAINT fk_book_copy_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE RESTRICT,
    CONSTRAINT ck_book_copy_barcode CHECK (CHAR_LENGTH(TRIM(barcode)) BETWEEN 1 AND 50),
    CONSTRAINT ck_book_copy_location CHECK (shelf_location IS NULL OR CHAR_LENGTH(shelf_location) <= 100),
    CONSTRAINT ck_book_copy_status CHECK (status IN ('AVAILABLE', 'ON_LOAN', 'WITHDRAWN')),
    CONSTRAINT ck_book_copy_version CHECK (version >= 0),
    INDEX ix_book_copy_book_status (book_id, status)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
