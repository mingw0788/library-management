package com.example.library.loan;

import org.springframework.stereotype.Component;

/**
 * Central extension point for the reservation rule added by X02.
 * X01 has no reservation table or active reservation states, so this guard currently permits renewal.
 */
@Component
public class ReservationRenewalGuard {

    public void assertNoActiveReservation(Long readerProfileId, Long bookId) {
        // Reservation eligibility will be checked here when the reservation model is introduced in X02.
    }
}
