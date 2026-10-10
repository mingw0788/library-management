package com.example.library.dashboard;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record LoanReminderItem(Long loanId, Long bookId, String title, LocalDate dueDate) {
    public String dueLabel(LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, dueDate);
        if (days < 0) return "已逾期 " + (-days) + " 天";
        if (days == 0) return "今天应还";
        return "还有 " + days + " 天应还";
    }
}
