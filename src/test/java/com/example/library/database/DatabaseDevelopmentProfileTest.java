package com.example.library.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("dev")
class DatabaseDevelopmentProfileTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void developmentProfileUsesOnlyDevelopmentDatabaseAndUtcReadCommittedSessions() {
        assertEquals("library_dev", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("READ-COMMITTED", jdbcTemplate.queryForObject(
                "SELECT @@session.transaction_isolation", String.class));
        assertEquals("+00:00", jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_guard WHERE guard_key = 'ACTIVE_ADMIN_SET'", Integer.class));
    }
}
