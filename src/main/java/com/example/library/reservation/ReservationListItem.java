package com.example.library.reservation;

import java.time.Instant;

public record ReservationListItem(Long reservationId, Long bookId, String bookTitle,
                                  ReservationStatus status, Instant createdAt, Long queuePosition,
                                  Instant holdExpiresAt) {
}
