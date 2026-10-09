package com.example.library.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountModelTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void normalizesLoginAndStoresOnlyAOneWayPasswordHash() {
        String rawPassword = "library-pass-12";
        AppUser user = accountService.createUser("  Test_User  ", rawPassword, " 测试读者 ", UserRole.READER);

        assertEquals("test_user", user.getLoginName());
        assertEquals("测试读者", user.getDisplayName());
        assertEquals(UserRole.READER, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertTrue(user.isMustChangePassword());
        assertEquals(0, user.getCredentialVersion());

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM app_user WHERE id = ?", String.class, user.getId());
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT created_at FROM app_user WHERE id = ?", java.time.LocalDateTime.class, user.getId()));
        assertNotEquals(rawPassword, storedHash);
        assertTrue(passwordEncoder.matches(rawPassword, storedHash));
        assertFalse(passwordEncoder.matches("library-pass-13", storedHash));
        assertEquals(user.getId(), accountService.findByLoginName(" TEST_USER ").orElseThrow().getId());
    }

    @Test
    void normalizedLoginNamesAreUniqueInMysql() {
        accountService.createUser(" Reader_01 ", "library-pass-12", "读者一", UserRole.READER);
        assertThrows(DataIntegrityViolationException.class, () -> {
            accountService.createUser("reader_01", "another-pass-12", "读者二", UserRole.READER);
            users.flush();
        });
    }

    @Test
    void mysqlRejectsRolesAndStatusesOutsideTheDefinedEnums() {
        assertThrows(DataAccessException.class, () -> insertDirect("bad_role", "ROOT", "ACTIVE"));
        assertThrows(DataAccessException.class, () -> insertDirect("bad_status", "READER", "PENDING"));
    }

    @Test
    void enforcesRequiredFieldsAndCharacterAndBcryptByteLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> accountService.createUser("valid_name", null, "姓名", UserRole.READER));
        assertThrows(IllegalArgumentException.class,
                () -> accountService.createUser("valid_name", "short-pass1", "姓名", UserRole.READER));
        assertThrows(IllegalArgumentException.class,
                () -> accountService.createUser("valid_name", "a".repeat(65), "姓名", UserRole.READER));
        assertThrows(IllegalArgumentException.class,
                () -> accountService.createUser("valid_name", "中".repeat(25), "姓名", UserRole.READER));
        assertThrows(IllegalArgumentException.class,
                () -> accountService.createUser("valid_name", "library-pass-12", "  ", UserRole.READER));
        assertThrows(IllegalArgumentException.class,
                () -> accountService.createUser("ab", "library-pass-12", "姓名", UserRole.READER));

        AppUser maximumByteBoundary = accountService.createUser("valid_name", "中".repeat(24), "姓名", UserRole.READER);
        assertTrue(passwordEncoder.matches("中".repeat(24), maximumByteBoundary.getPasswordHash()));
        AppUser maximumCharacters = accountService.createUser("valid_name_2", "a".repeat(64), "姓名", UserRole.READER);
        assertTrue(passwordEncoder.matches("a".repeat(64), maximumCharacters.getPasswordHash()));
    }

    @Test
    void statusChangesAdvanceCredentialVersionWithoutChangingRole() {
        AppUser user = accountService.createUser("versioned_user", "library-pass-12", "测试", UserRole.LIBRARIAN);
        user.changeStatus(UserStatus.DISABLED);
        users.flush();

        AppUser reloaded = users.findById(user.getId()).orElseThrow();
        assertEquals(UserStatus.DISABLED, reloaded.getStatus());
        assertEquals(UserRole.LIBRARIAN, reloaded.getRole());
        assertEquals(1, reloaded.getCredentialVersion());
    }

    private void insertDirect(String loginName, String role, String status) {
        jdbcTemplate.update("""
                INSERT INTO app_user (login_name, password_hash, display_name, role, status,
                                      must_change_password, credential_version)
                VALUES (?, ?, ?, ?, ?, TRUE, 0)
                """, loginName, "not-a-real-password", "测试", role, status);
    }
}
