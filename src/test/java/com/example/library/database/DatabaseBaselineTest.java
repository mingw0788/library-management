package com.example.library.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class DatabaseBaselineTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectsOnlyToTheDedicatedTestDatabase() {
        assertEquals("library_test", jdbcTemplate.queryForObject("SELECT DATABASE()", String.class));
    }

    @Test
    void configuresReadCommittedAndUtcForEachConnection() {
        assertEquals("READ-COMMITTED", jdbcTemplate.queryForObject(
                "SELECT @@session.transaction_isolation", String.class));
        assertEquals("+00:00", jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class));
    }

    @Test
    void flywayCreatesExactlyOneAdministratorGuardLockRow() {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM system_guard WHERE guard_key = 'ACTIVE_ADMIN_SET'", Integer.class));
    }
}
