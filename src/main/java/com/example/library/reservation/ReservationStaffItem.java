package com.example.library.reservation;

import java.time.Instant;

public record ReservationStaffItem(Long reservationId, Long readerId, String readerNo,
                                    String readerName, String bookTitle, Instant createdAt,
                                    Instant holdExpiresAt) {
}
