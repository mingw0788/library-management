package com.example.library.web;

import com.example.library.loan.CirculationPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CirculationPresentationTest {
    @Test
    void readerReturnRetainsValidFiltersAndRejectsUntrustedTargets() {
        assertEquals("/reader/loans?state=ALL&page=2&fromDate=2026-10-01&toDate=2026-10-10",
                LoanListReturn.target(Map.of("listState", "ALL", "listPage", "2",
                        "listFromDate", "2026-10-01", "listToDate", "2026-10-10", "returnUrl", "https://example.com")));
        assertEquals("/reader/loans", LoanListReturn.target(Map.of("returnUrl", "//example.com")));
        for (Map<String, String> invalid : java.util.List.of(
                Map.of("listState", "invalid"), Map.of("listPage", "100001"),
                Map.of("listPage", "-1"), Map.of("listPage", "bad"),
                Map.of("listFromDate", "2026-02-30"))) {
            assertEquals("/reader/loans", LoanListReturn.target(invalid));
        }
    }

    @Test
    void reservationTimeUsesConfiguredBusinessZoneAcrossUtcDateBoundary() {
        DisplayTime time = new DisplayTime(new CirculationPolicy(5, 14, "Asia/Shanghai"));
        assertEquals("2026-10-11 01:30", time.format(Instant.parse("2026-10-10T17:30:00Z")));
        assertEquals("—", time.format(null));
        assertEquals("Asia/Shanghai", time.zone());
        assertEquals("2026-10-10 17:30", new DisplayTime(new CirculationPolicy(5, 14, "UTC"))
                .format(Instant.parse("2026-10-10T17:30:00Z")));
    }
}
