CREATE TABLE loan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reader_id BIGINT NOT NULL,
    copy_id BIGINT NOT NULL,
    borrowed_at DATETIME(6) NOT NULL,
    due_date DATE NOT NULL,
    returned_at DATETIME(6) NULL,
    checkout_operator_id BIGINT NOT NULL,
    return_operator_id BIGINT NULL,
    active_copy_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN returned_at IS NULL THEN copy_id ELSE NULL END) STORED,
    renew_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_loan_active_copy UNIQUE (active_copy_id),
    CONSTRAINT fk_loan_reader FOREIGN KEY (reader_id) REFERENCES reader_profile (id) ON DELETE RESTRICT,
    CONSTRAINT fk_loan_copy FOREIGN KEY (copy_id) REFERENCES book_copy (id) ON DELETE RESTRICT,
    CONSTRAINT fk_loan_checkout_operator FOREIGN KEY (checkout_operator_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT fk_loan_return_operator FOREIGN KEY (return_operator_id) REFERENCES app_user (id) ON DELETE RESTRICT,
    CONSTRAINT ck_loan_return_pair CHECK (
        (returned_at IS NULL AND return_operator_id IS NULL) OR
        (returned_at IS NOT NULL AND return_operator_id IS NOT NULL)
    ),
    CONSTRAINT ck_loan_return_time CHECK (returned_at IS NULL OR returned_at >= borrowed_at),
    CONSTRAINT ck_loan_renew_count CHECK (renew_count = 0),
    CONSTRAINT ck_loan_version CHECK (version >= 0),
    INDEX ix_loan_reader_open_due (reader_id, returned_at, due_date),
    INDEX ix_loan_open_due (returned_at, due_date),
    INDEX ix_loan_copy_history (copy_id, borrowed_at, id)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
