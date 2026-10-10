package com.example.library.web;

import com.example.library.loan.CirculationPolicy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Component("displayTime")
public class DisplayTime {
    private final CirculationPolicy policy;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public DisplayTime(CirculationPolicy policy) { this.policy = policy; }

    public String format(Instant value) {
        return value == null ? "—" : formatter.format(value.atZone(policy.zoneId()));
    }

    public String zone() { return policy.businessTimeZone(); }
}
