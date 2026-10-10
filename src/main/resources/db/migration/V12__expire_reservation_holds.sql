ALTER TABLE reservation
    DROP CHECK ck_reservation_status,
    DROP CHECK ck_reservation_cancelled_at,
    DROP CHECK ck_reservation_assignment,
    DROP CHECK ck_reservation_time_order,
    ADD COLUMN expired_at DATETIME(6) NULL AFTER fulfilled_at;

ALTER TABLE reservation
    ADD CONSTRAINT ck_reservation_status CHECK (
        status IN ('PENDING', 'READY', 'FULFILLED', 'CANCELLED', 'EXPIRED')
    ),
    ADD CONSTRAINT ck_reservation_cancelled_at CHECK (
        (status IN ('PENDING', 'READY', 'FULFILLED', 'EXPIRED') AND cancelled_at IS NULL) OR
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_reservation_assignment CHECK (
        (status = 'PENDING' AND copy_id IS NULL AND hold_expires_at IS NULL
            AND fulfilled_at IS NULL AND expired_at IS NULL) OR
        (status = 'READY' AND copy_id IS NOT NULL AND hold_expires_at IS NOT NULL
            AND fulfilled_at IS NULL AND expired_at IS NULL) OR
        (status = 'FULFILLED' AND copy_id IS NOT NULL AND hold_expires_at IS NOT NULL
            AND fulfilled_at IS NOT NULL AND expired_at IS NULL) OR
        (status = 'CANCELLED' AND fulfilled_at IS NULL AND expired_at IS NULL AND
            ((copy_id IS NULL AND hold_expires_at IS NULL) OR
             (copy_id IS NOT NULL AND hold_expires_at IS NOT NULL))) OR
        (status = 'EXPIRED' AND copy_id IS NOT NULL AND hold_expires_at IS NOT NULL
            AND fulfilled_at IS NULL AND expired_at IS NOT NULL)
    ),
    ADD CONSTRAINT ck_reservation_time_order CHECK (
        (cancelled_at IS NULL OR cancelled_at >= created_at) AND
        (hold_expires_at IS NULL OR hold_expires_at >= created_at) AND
        (fulfilled_at IS NULL OR
            (fulfilled_at >= created_at AND fulfilled_at < hold_expires_at)) AND
        (expired_at IS NULL OR
            (expired_at >= created_at AND expired_at >= hold_expires_at))
    );
