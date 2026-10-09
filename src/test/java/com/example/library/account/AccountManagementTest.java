package com.example.library.account;

import com.example.library.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountManagementTest {

    private static final String PASSWORD = "account-test-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private ReaderProfileRepository profiles;
    @Autowired private PasswordEncoder encoder;

    private final List<Long> fixtureIds = new ArrayList<>();
    private Fixture admin;
    private Fixture librarian;
    private Fixture reader;

    @BeforeEach
    void prepare() {
        admin = fixture(UserRole.ADMIN);
        librarian = fixture(UserRole.LIBRARIAN);
        reader = fixture(UserRole.READER);
    }

    @AfterEach
    void clean() {
        fixtureIds.forEach(id -> {
            profiles.findByUserId(id).ifPresent(profiles::delete);
            users.findById(id).ifPresent(users::delete);
        });
        fixtureIds.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void roleBoundariesAndForgedRoleDoNotAuthorizeAccountManagement() throws Exception {
        mvc.perform(get("/admin/accounts")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/staff/readers")).andExpect(status().is3xxRedirection());
        MockHttpSession librarianSession = login(librarian);
        mvc.perform(get("/admin/accounts").session(librarianSession)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/accounts").with(csrf()).session(librarianSession)
                        .param("loginName", "forged_admin").param("initialPassword", PASSWORD)
                        .param("displayName", "伪造角色").param("role", "ADMIN"))
                .andExpect(status().isForbidden());
        MockHttpSession adminSession = login(admin);
        String createdStaffLogin = "webstaff_" + randomToken();
        mvc.perform(post("/admin/accounts").with(csrf()).session(adminSession)
                        .param("loginName", createdStaffLogin).param("initialPassword", PASSWORD)
                        .param("displayName", "网页馆员").param("role", "LIBRARIAN"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/accounts"));
        AppUser webCreated = users.findByLoginName(createdStaffLogin).orElseThrow();
        fixtureIds.add(webCreated.getId());
        assertEquals(UserRole.LIBRARIAN, webCreated.getRole());
        String csrfMissingLogin = "csrfmiss_" + randomToken();
        mvc.perform(post("/admin/accounts").session(adminSession)
                        .param("loginName", csrfMissingLogin).param("initialPassword", PASSWORD)
                        .param("displayName", "应被拒绝").param("role", "LIBRARIAN"))
                .andExpect(status().isForbidden());
        assertTrue(users.findByLoginName(csrfMissingLogin).isEmpty());
        mvc.perform(get("/staff/readers").session(librarianSession)).andExpect(status().isOk());
        MockHttpSession readerSession = login(reader);
        mvc.perform(get("/staff/readers").session(readerSession)).andExpect(status().isForbidden());
        mvc.perform(post("/staff/readers").with(csrf()).session(readerSession)
                        .param("loginName", "forged_reader").param("initialPassword", PASSWORD)
                        .param("displayName", "越权").param("readerNo", "F-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void readerCreationIsAtomicAndRejectsDuplicateLoginCardAndInvalidFields() throws Exception {
        String card = "CARD-" + randomToken();
        AppUser created = accounts.createReader(UserRole.ADMIN, "  Reader_" + randomToken(), PASSWORD,
                " 新读者 ", card, " contact ");
        fixtureIds.add(created.getId());
        assertTrue(created.isMustChangePassword());
        assertEquals("新读者", created.getDisplayName());
        ReaderProfile profile = profiles.findByUserId(created.getId()).orElseThrow();
        assertEquals(card, profile.getReaderNo());
        assertEquals("contact", profile.getContact());
        MockHttpSession newReaderSession = login(new Fixture(created.getId(), created.getLoginName(), UserRole.READER));
        mvc.perform(get("/reader").session(newReaderSession))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/account/password"));
        mvc.perform(get("/staff/readers").session(login(admin)))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString(card)));

        String duplicateCardLogin = "rollback_" + randomToken();
        assertThrows(RuntimeException.class, () -> accounts.createReader(UserRole.LIBRARIAN,
                duplicateCardLogin, PASSWORD, "回滚读者", card, null));
        assertTrue(users.findByLoginName(duplicateCardLogin).isEmpty(), "档案创建失败必须回滚账号");
        assertThrows(IllegalArgumentException.class, () -> accounts.createReader(UserRole.ADMIN,
                "bad_" + randomToken(), "short", "非法密码", "CARD-X", null));
        assertThrows(IllegalArgumentException.class, () -> accounts.createReader(UserRole.ADMIN,
                "bad_" + randomToken(), PASSWORD, "非法证号", "   ", null));
        assertThrows(IllegalArgumentException.class, () -> accounts.createReader(UserRole.ADMIN,
                "bad_" + randomToken(), PASSWORD, "非法联系方式", "CARD-Y", "x".repeat(101)));
        assertThrows(RuntimeException.class, () -> accounts.createReader(UserRole.ADMIN,
                created.getLoginName(), PASSWORD, "重复登录名", "CARD-Z", null));
    }

    @Test
    void readerMaintenanceKeepsCardStableAndResetInvalidatesOldSession() throws Exception {
        String loginName = "manage_" + randomToken();
        AppUser target = accounts.createReader(UserRole.ADMIN, loginName, PASSWORD, "原姓名", "STABLE-" + randomToken(), null);
        Long id = target.getId();
        fixtureIds.add(id);
        MockHttpSession session = login(new Fixture(id, loginName, UserRole.READER));
        long oldVersion = target.getCredentialVersion();

        accounts.updateReader(UserRole.LIBRARIAN, id, " 新姓名 ", " 123 ");
        assertEquals("新姓名", users.findById(id).orElseThrow().getDisplayName());
        assertEquals("123", profiles.findByUserId(id).orElseThrow().getContact());
        assertThrows(IllegalArgumentException.class,
                () -> accounts.updateReader(UserRole.ADMIN, id, "姓名", "z".repeat(101)));
        assertThrows(IllegalArgumentException.class, () -> accounts.resetReaderPassword(UserRole.ADMIN, reader.id(), PASSWORD));

        accounts.resetReaderPassword(UserRole.LIBRARIAN, id, "reset-account-pass-456");
        AppUser afterReset = users.findById(id).orElseThrow();
        assertEquals(oldVersion + 1, afterReset.getCredentialVersion());
        assertTrue(afterReset.isMustChangePassword());
        assertTrue(encoder.matches("reset-account-pass-456", afterReset.getPasswordHash()));
        mvc.perform(get("/reader").session(session))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?expired=true"));
        assertTrue(session.isInvalid());

        long beforeDisable = afterReset.getCredentialVersion();
        accounts.disableReader(UserRole.ADMIN, id);
        AppUser disabled = users.findById(id).orElseThrow();
        assertEquals(UserStatus.DISABLED, disabled.getStatus());
        assertEquals(beforeDisable + 1, disabled.getCredentialVersion());
    }

    @Test
    void creatingAndResettingStaffEnforcesRoleAndForcedPasswordChange() {
        AppUser created = accounts.createStaff(UserRole.ADMIN, "staff_" + randomToken(), PASSWORD,
                "新馆员", UserRole.LIBRARIAN);
        fixtureIds.add(created.getId());
        assertEquals(UserRole.LIBRARIAN, created.getRole());
        assertTrue(created.isMustChangePassword());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> accounts.createStaff(UserRole.LIBRARIAN, "nope_" + randomToken(), PASSWORD, "越权", UserRole.LIBRARIAN));
        assertThrows(IllegalArgumentException.class,
                () -> accounts.createStaff(UserRole.ADMIN, "badrole_" + randomToken(), PASSWORD, "越权", UserRole.READER));
        accounts.resetStaffPassword(UserRole.ADMIN, created.getId(), "new-staff-pass-456");
        AppUser afterReset = users.findById(created.getId()).orElseThrow();
        assertTrue(afterReset.isMustChangePassword());
        assertTrue(encoder.matches("new-staff-pass-456", afterReset.getPasswordHash()));
        accounts.disableStaff(UserRole.ADMIN, created.getId());
        AppUser disabled = users.findById(created.getId()).orElseThrow();
        assertEquals(UserStatus.DISABLED, disabled.getStatus());
        assertTrue(disabled.getCredentialVersion() >= 2);
    }

    @Test
    void concurrentDisableOfTwoAdminsCannotRemoveTheLastActiveAdmin() throws Exception {
        Fixture second = fixture(UserRole.ADMIN);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var firstResult = pool.submit(() -> disableAtSameTime(admin.id(), ready, start));
            var secondResult = pool.submit(() -> disableAtSameTime(second.id(), ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            boolean firstDisabled = firstResult.get(20, TimeUnit.SECONDS);
            boolean secondDisabled = secondResult.get(20, TimeUnit.SECONDS);
            assertNotEquals(firstDisabled, secondDisabled, "恰有一项禁用成功，另一项因只剩最后管理员而拒绝");
        }
        assertTrue(users.existsByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE));
    }

    private boolean disableAtSameTime(Long id, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            accounts.disableStaff(UserRole.ADMIN, id);
            return true;
        } catch (IllegalArgumentException expectedWhenLastAdmin) {
            return false;
        }
    }

    private Fixture fixture(UserRole role) {
        String name = "t07_" + randomToken();
        AppUser user = accounts.createUser(name, PASSWORD, "T07测试", role);
        user.markPasswordChanged();
        users.saveAndFlush(user);
        fixtureIds.add(user.getId());
        return new Fixture(user.getId(), name, role);
    }

    private MockHttpSession login(Fixture fixture) throws Exception {
        var result = mvc.perform(post("/login").with(csrf())
                        .param("username", fixture.loginName().toUpperCase(Locale.ROOT)).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String randomToken() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private record Fixture(Long id, String loginName, UserRole role) {}
}
