package com.example.library.reservation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@Profile({"dev", "test", "prod"})
@ConditionalOnProperty(prefix = "library.reservations", name = "enabled", havingValue = "true")
@EnableScheduling
public class ReservationSchedulingConfiguration {
}
