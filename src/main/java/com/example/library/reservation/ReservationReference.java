package com.example.library.reservation;

public record ReservationReference(Long reservationId, Long readerId, Long readerUserId, Long bookId) {
}
