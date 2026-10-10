package com.example.library.reservation;

import com.example.library.account.UserStatus;
import com.example.library.account.UserRole;

import java.time.Instant;

public record ReservationQueueCandidate(Long reservationId, Long readerId, Long readerUserId,
                                         UserRole readerRole, UserStatus readerStatus, Instant createdAt) {
}
