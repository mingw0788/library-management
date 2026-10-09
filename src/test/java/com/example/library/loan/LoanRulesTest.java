package com.example.library.loan;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class LoanRulesTest {

    private final CirculationPolicy policy = new CirculationPolicy(5, 14, "Asia/Shanghai");

    @Test
    void dueDateUsesShanghaiDateAcrossMonthAndYearBoundaries() {
        LoanRules rules = new LoanRules(policy, Clock.fixed(Instant.parse("2026-01-31T16:30:00Z"), ZoneId.of("UTC")));
        assertEquals(LocalDate.of(2026, 2, 15), rules.dueDateNow());
        assertEquals(LocalDate.of(2026, 2, 15), rules.dueDateFor(Instant.parse("2026-01-31T16:30:00Z")));
        assertEquals(LocalDate.of(2027, 1, 15), rules.dueDateFor(Instant.parse("2026-12-31T16:30:00Z")));
        assertEquals(LocalDate.of(2024, 3, 14), rules.dueDateFor(Instant.parse("2024-02-28T16:30:00Z")));
    }

    @Test
    void dueDateIsNotOverdueUntilTheNextShanghaiCalendarDay() {
        LoanRules rules = new LoanRules(policy, Clock.systemUTC());
        LocalDate dueDate = LocalDate.of(2026, 10, 9);
        assertFalse(rules.isOverdue(dueDate, Instant.parse("2026-10-09T15:59:59Z")));
        assertTrue(rules.isOverdue(dueDate, Instant.parse("2026-10-09T16:00:00Z")));
        assertThrows(IllegalArgumentException.class, () -> rules.dueDateFor(null));
    }

    @Test
    void circulationConfigurationRejectsNonPositiveValuesAndUnknownTimeZones() {
        assertThrows(IllegalArgumentException.class, () -> new CirculationPolicy(0, 14, "Asia/Shanghai"));
        assertThrows(IllegalArgumentException.class, () -> new CirculationPolicy(5, 0, "Asia/Shanghai"));
        assertThrows(IllegalArgumentException.class, () -> new CirculationPolicy(5, 14, "Invalid/Zone"));
    }
}
