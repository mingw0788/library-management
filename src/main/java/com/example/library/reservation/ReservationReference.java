package com.example.library.reservation;

import java.time.Instant;

public record ReservationReference(Long reservationId, Long readerId, Long readerUserId, Long bookId,
                                   Long copyId, ReservationStatus status, Instant holdExpiresAt) {
}
