ALTER TABLE reservation
    DROP INDEX uk_reservation_active_reader_book,
    DROP CHECK ck_reservation_status,
    DROP CHECK ck_reservation_cancelled_at,
    DROP COLUMN active_reader_id;

ALTER TABLE reservation
    ADD COLUMN copy_id BIGINT NULL AFTER book_id,
    ADD COLUMN hold_expires_at DATETIME(6) NULL AFTER cancelled_at,
    ADD COLUMN fulfilled_at DATETIME(6) NULL AFTER hold_expires_at,
    ADD COLUMN active_reader_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING', 'READY') THEN reader_id ELSE NULL END) STORED,
    ADD COLUMN active_copy_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'READY' THEN copy_id ELSE NULL END) STORED,
    ADD CONSTRAINT fk_reservation_copy FOREIGN KEY (copy_id) REFERENCES book_copy (id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_reservation_active_reader_book UNIQUE (book_id, active_reader_id),
    ADD CONSTRAINT uk_reservation_active_copy UNIQUE (active_copy_id),
    ADD CONSTRAINT ck_reservation_status CHECK (status IN ('PENDING', 'READY', 'FULFILLED', 'CANCELLED')),
    ADD CONSTRAINT ck_reservation_cancelled_at CHECK (
        (status IN ('PENDING', 'READY', 'FULFILLED') AND cancelled_at IS NULL) OR
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_reservation_assignment CHECK (
        (status = 'PENDING' AND copy_id IS NULL AND hold_expires_at IS NULL AND fulfilled_at IS NULL) OR
        (status = 'READY' AND copy_id IS NOT NULL AND hold_expires_at IS NOT NULL AND fulfilled_at IS NULL) OR
        (status = 'FULFILLED' AND copy_id IS NOT NULL AND hold_expires_at IS NOT NULL AND fulfilled_at IS NOT NULL) OR
        (status = 'CANCELLED' AND fulfilled_at IS NULL AND
            ((copy_id IS NULL AND hold_expires_at IS NULL) OR (copy_id IS NOT NULL AND hold_expires_at IS NOT NULL)))
    ),
    ADD CONSTRAINT ck_reservation_time_order CHECK (
        (cancelled_at IS NULL OR cancelled_at >= created_at) AND
        (hold_expires_at IS NULL OR hold_expires_at >= created_at) AND
        (fulfilled_at IS NULL OR fulfilled_at >= created_at)
    );

ALTER TABLE book_copy
    DROP CHECK ck_book_copy_status,
    ADD CONSTRAINT ck_book_copy_status CHECK (status IN ('AVAILABLE', 'ON_LOAN', 'HELD', 'WITHDRAWN'));
