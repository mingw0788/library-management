package com.example.library.reservation;

/** A read-only hint; submission always rechecks eligibility under the existing locks. */
public record BookReservationOffer(boolean canReserve, String message) {}
