package com.example.library.loan;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.DateTimeException;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "library.circulation")
public record CirculationPolicy(int maximumActiveLoans, int loanPeriodDays, String businessTimeZone) {

    public CirculationPolicy {
        if (maximumActiveLoans < 1) throw new IllegalArgumentException("同时借阅上限必须大于 0");
        if (loanPeriodDays < 1) throw new IllegalArgumentException("借期必须大于 0 天");
        try {
            ZoneId.of(businessTimeZone);
        } catch (DateTimeException | NullPointerException exception) {
            throw new IllegalArgumentException("业务时区配置无效", exception);
        }
    }

    public ZoneId zoneId() { return ZoneId.of(businessTimeZone); }
}
