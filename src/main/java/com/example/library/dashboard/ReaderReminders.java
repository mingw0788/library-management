package com.example.library.dashboard;

import java.time.LocalDate;
import java.util.List;

public record ReaderReminders(boolean profileAvailable, LocalDate today, String timeZone,
                              boolean reservationsEnabled, long overdueCount, long dueSoonCount, long readyCount,
                              List<LoanReminderItem> overdue, List<LoanReminderItem> dueSoon,
                              List<ReadyReminderItem> ready) {}
