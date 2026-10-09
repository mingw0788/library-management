package com.example.library.dashboard;

public record StaffDashboardStats(long bookTitles, long copiesTotal, long copiesValid,
                                  long copiesAvailable, long currentLoans, long currentOverdueLoans) {
}
