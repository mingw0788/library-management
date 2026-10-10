package com.example.library.reservation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@Profile({"dev", "test", "prod"})
@ConditionalOnProperty(prefix = "library.reservations", name = "enabled", havingValue = "true")
public class ReservationExpiryScheduler {

    private final ReservationRepository reservations;
    private final ReservationService reservationService;
    private final Clock circulationClock;

    public ReservationExpiryScheduler(ReservationRepository reservations, ReservationService reservationService,
                                      Clock circulationClock) {
        this.reservations = reservations;
        this.reservationService = reservationService;
        this.circulationClock = circulationClock;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireDueReservations() {
        for (Long reservationId : reservations.findExpiredReadyReservationIds(
                ReservationStatus.READY, circulationClock.instant())) {
            reservationService.expireDueReservation(reservationId);
        }
    }
}
