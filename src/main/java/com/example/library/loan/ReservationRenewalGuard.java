package com.example.library.loan;

import com.example.library.reservation.ReservationRepository;
import com.example.library.reservation.ReservationStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prevents extending a loan while readers are waiting for or holding this book.
 */
@Component
public class ReservationRenewalGuard {

    private final ReservationRepository reservations;

    public ReservationRenewalGuard(ReservationRepository reservations) {
        this.reservations = reservations;
    }

    public void assertNoActiveReservation(Long readerProfileId, Long bookId) {
        if (reservations.existsByBookIdAndStatusIn(bookId,
                List.of(ReservationStatus.PENDING, ReservationStatus.READY))) {
            throw new IllegalArgumentException("该书目有活动预约，不能续借");
        }
    }
}
