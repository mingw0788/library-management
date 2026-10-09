package com.example.library.loan;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoanViewMapperTest {

    private final CirculationPolicy policy = new CirculationPolicy(5, 14, "Asia/Shanghai");

    @Test
    void separatesCurrentOverdueFromReturnedLateAndUsesShanghaiDayBoundary() {
        Instant beforeShanghaiMidnight = Instant.parse("2026-10-09T15:59:59Z");
        Instant atShanghaiMidnight = Instant.parse("2026-10-09T16:00:00Z");
        LocalDate dueDate = LocalDate.of(2026, 10, 9);
        LoanViewRow active = row(dueDate, null);
        LoanViewRow returnedOnTime = row(dueDate, beforeShanghaiMidnight);
        LoanViewRow returnedLate = row(dueDate, atShanghaiMidnight);

        LoanListItem beforeBoundary = mapper("2026-10-09T15:59:59Z").toListItem(active);
        LoanListItem afterBoundary = mapper("2026-10-09T16:00:00Z").toListItem(active);
        LoanListItem onTime = mapper("2026-10-10T04:00:00Z").toListItem(returnedOnTime);
        LoanListItem late = mapper("2026-10-10T04:00:00Z").toListItem(returnedLate);
        LoanListItem monthBoundary = mapper("2026-11-01T00:00:00Z")
                .toListItem(row(LocalDate.of(2026, 10, 31), null));
        LoanListItem yearBoundary = mapper("2027-01-01T00:00:00Z")
                .toListItem(row(LocalDate.of(2026, 12, 31), null));

        assertFalse(beforeBoundary.overdue());
        assertTrue(afterBoundary.overdue());
        assertFalse(onTime.overdue());
        assertFalse(onTime.returnedLate());
        assertFalse(late.overdue());
        assertTrue(late.returnedLate());
        assertTrue(monthBoundary.overdue());
        assertTrue(yearBoundary.overdue());
    }

    private LoanViewMapper mapper(String now) {
        Clock clock = Clock.fixed(Instant.parse(now), ZoneId.of("UTC"));
        return new LoanViewMapper(new LoanRules(policy, clock), clock);
    }

    private static LoanViewRow row(LocalDate dueDate, Instant returnedAt) {
        return new LoanViewRow(1L, "R-1", "测试读者", "BC-1", "测试图书",
                Instant.parse("2026-10-01T03:00:00Z"), dueDate, returnedAt, "测试馆员",
                returnedAt == null ? null : "测试馆员");
    }
}
