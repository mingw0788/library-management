package com.example.library.database;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReservationProfileConfigurationTest {

    private static final String RESERVATIONS_ENABLED = "library.reservations.enabled";

    @Test
    void productionEnablesCompletedExtensionWhileDefaultAndDevelopmentRemainClosed() throws IOException {
        assertEquals(Boolean.FALSE, load("application.yml").getProperty(RESERVATIONS_ENABLED));
        assertEquals(Boolean.TRUE, load("application-test.yml").getProperty(RESERVATIONS_ENABLED));
        assertEquals(Boolean.TRUE, load("application-prod.yml").getProperty(RESERVATIONS_ENABLED));
        assertNull(load("application-dev.yml").getProperty(RESERVATIONS_ENABLED));
    }

    @Test
    void expiryScanRunsOncePerMinute() throws NoSuchMethodException {
        Scheduled schedule = com.example.library.reservation.ReservationExpiryScheduler.class
                .getMethod("expireDueReservations").getAnnotation(Scheduled.class);

        assertEquals(60_000L, schedule.fixedDelay());
        assertEquals(60_000L, schedule.initialDelay());
    }

    private PropertySource<?> load(String file) throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load(file, new ClassPathResource(file));
        return propertySources.get(0);
    }
}
