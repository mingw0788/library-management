package com.example.library.loan;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class CirculationConfiguration {

    @Bean
    Clock circulationClock() {
        return Clock.systemUTC();
    }
}
