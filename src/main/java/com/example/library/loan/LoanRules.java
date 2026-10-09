package com.example.library.loan;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Component
public class LoanRules {

    private final CirculationPolicy policy;
    private final Clock clock;

    public LoanRules(CirculationPolicy policy, Clock circulationClock) {
        this.policy = policy;
        this.clock = circulationClock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(policy.zoneId()));
    }

    public LocalDate dueDateFor(Instant borrowedAt) {
        if (borrowedAt == null) throw new IllegalArgumentException("借出时刻不能为空");
        return borrowedAt.atZone(policy.zoneId()).toLocalDate().plusDays(policy.loanPeriodDays());
    }

    public LocalDate dueDateNow() {
        return dueDateFor(clock.instant());
    }

    public boolean isOverdue(LocalDate dueDate, Instant at) {
        if (dueDate == null || at == null) throw new IllegalArgumentException("应还日期和判断时刻不能为空");
        return at.atZone(policy.zoneId()).toLocalDate().isAfter(dueDate);
    }

    public boolean isReturnedLate(LocalDate dueDate, Instant returnedAt) {
        if (dueDate == null || returnedAt == null) throw new IllegalArgumentException("应还日期和归还时刻不能为空");
        return returnedAt.atZone(policy.zoneId()).toLocalDate().isAfter(dueDate);
    }

    public CirculationPolicy policy() { return policy; }
}
