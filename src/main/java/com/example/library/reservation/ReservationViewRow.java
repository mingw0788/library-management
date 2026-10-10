package com.example.library.reservation;

import java.time.Instant;

public record ReservationViewRow(Long reservationId, Long bookId, String bookTitle,
                                 ReservationStatus status, Instant createdAt, Instant holdExpiresAt) {
}
