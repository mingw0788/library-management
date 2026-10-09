package com.example.library.account;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class AdminBootstrapTest {

    @org.springframework.beans.factory.annotation.Autowired
    private AppUserRepository users;

    @org.springframework.beans.factory.annotation.Autowired
    private AccountService accounts;

    @org.springframework.beans.factory.annotation.Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void explicitBootstrapCreatesOneAdminAndNeverResetsItOnRepeatStartup() {
        String loginName = "bootstrap_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        assertFalse(users.existsByRole(UserRole.ADMIN));
        AdminBootstrapRunner firstStart = new AdminBootstrapRunner(true, loginName, "first-pass-12345",
                "首次管理员", accounts);
        AdminBootstrapRunner repeatedStart = new AdminBootstrapRunner(true, loginName, "second-pass-67890",
                "不应覆盖", accounts);

        try {
            firstStart.initializeIfNecessary();
            AppUser created = users.findByLoginName(loginName).orElseThrow();
            String originalHash = created.getPasswordHash();
            long accountCount = users.count();
            repeatedStart.initializeIfNecessary();

            AppUser afterRepeat = users.findByLoginName(loginName).orElseThrow();
            assertEquals(accountCount, users.count());
            assertEquals(originalHash, afterRepeat.getPasswordHash());
            assertTrue(passwordEncoder.matches("first-pass-12345", afterRepeat.getPasswordHash()));
            assertFalse(passwordEncoder.matches("second-pass-67890", afterRepeat.getPasswordHash()));
            assertEquals("首次管理员", afterRepeat.getDisplayName());
            assertEquals(UserRole.ADMIN, afterRepeat.getRole());
            assertTrue(afterRepeat.isMustChangePassword());
        } finally {
            users.findByLoginName(loginName).ifPresent(users::delete);
        }
    }

    @Test
    void enabledBootstrapWithoutCredentialsFailsWithoutCreatingAnAccount() {
        AccountService service = mock(AccountService.class);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(true, "", "", "", service);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));

        assertTrue(exception.getMessage().contains("LIBRARY_INITIAL_ADMIN_USERNAME"));
        verify(service, never()).initializeFirstAdmin(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void disabledBootstrapDoesNothing() {
        AppUserRepository repository = mock(AppUserRepository.class);
        AccountService service = mock(AccountService.class);
        AdminBootstrapRunner runner = new AdminBootstrapRunner(false, "name", "password", "显示名", service);

        runner.initializeIfNecessary();

        verify(service, never()).initializeFirstAdmin(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
