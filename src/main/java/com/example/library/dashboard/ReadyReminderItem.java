package com.example.library.dashboard;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public record ReadyReminderItem(Long reservationId, Long bookId, String title, Instant holdExpiresAt) {
    public String deadline(String timeZone) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(holdExpiresAt.atZone(ZoneId.of(timeZone)));
    }
}
