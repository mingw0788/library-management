package com.example.library.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class DatabaseDevelopmentProfileTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MockMvc mvc;

    @Test
    void developmentProfileUsesOnlyDevelopmentDatabaseAndUtcReadCommittedSessions() {
        assertEquals("library_dev", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("READ-COMMITTED", jdbcTemplate.queryForObject(
                "SELECT @@session.transaction_isolation", String.class));
        assertEquals("+00:00", jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_guard WHERE guard_key = 'ACTIVE_ADMIN_SET'", Integer.class));
        assertEquals("12", jdbcTemplate.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = 1 " +
                        "ORDER BY installed_rank DESC LIMIT 1", String.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() " +
                        "AND table_name = 'loan'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() " +
                        "AND table_name = 'audit_log'", Integer.class));
    }

    @Test
    void reservationFeatureIsUnavailableInDevelopmentProfileByDefault() throws Exception {
        assertFalse(applicationContext.getEnvironment()
                .getProperty("library.reservations.enabled", Boolean.class, false));
        assertFalse(applicationContext.containsBean("reservationService"));
        assertFalse(applicationContext.containsBean("reservationController"));
        assertFalse(applicationContext.containsBean("staffReservationController"));
        assertFalse(applicationContext.containsBean("reservationExpiryScheduler"));
        assertFalse(applicationContext.containsBean("reservationSchedulingConfiguration"));

        mvc.perform(get("/reader/reservations").with(user("test-reader").roles("READER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/reader/reservations").with(user("test-reader").roles("READER"))
                        .with(csrf()).param("bookId", "1"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/reader/reservations/1/cancel").with(user("test-reader").roles("READER"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/staff/reservations").with(user("test-staff").roles("LIBRARIAN")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/staff/reservations/1/fulfill").with(user("test-staff").roles("LIBRARIAN"))
                        .with(csrf()).param("readerNo", "TEST-1"))
                .andExpect(status().isNotFound());
    }
}
