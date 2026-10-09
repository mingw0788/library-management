package com.example.library.account;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OwnAccountTest {

    private static final String PASSWORD = "own-account-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private ReaderProfileRepository readerProfiles;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private Fixture admin;
    private Fixture librarian;
    private Fixture reader;

    @BeforeEach
    void prepare() {
        SecurityContextHolder.clearContext();
        admin = activeUser(UserRole.ADMIN);
        librarian = activeUser(UserRole.LIBRARIAN);
        String readerLogin = "self_reader_" + token();
        String readerNo = "SELF-" + token();
        AppUser readerUser = accounts.createReader(UserRole.ADMIN, readerLogin, PASSWORD,
                "读者原姓名", readerNo, "原联系方式");
        readerUser.markPasswordChanged();
        users.saveAndFlush(readerUser);
        userIds.add(readerUser.getId());
        reader = new Fixture(readerUser.getId(), readerLogin, UserRole.READER, readerNo);
    }

    @AfterEach
    void clean() {
        for (Long userId : userIds) {
            readerProfiles.findByUserId(userId).ifPresent(readerProfiles::delete);
            users.findById(userId).ifPresent(users::delete);
        }
        users.flush();
        userIds.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void everyRoleCanOpenOnlyItsOwnProfilePage() throws Exception {
        mvc.perform(get("/account/profile"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));

        mvc.perform(get("/account/profile").session(login(admin)))
                .andExpect(status().isOk()).andExpect(view().name("account/profile"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"displayName\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"contact\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"role\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
        mvc.perform(get("/account/profile").session(login(librarian)))
                .andExpect(status().isOk()).andExpect(view().name("account/profile"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"contact\""))));
        mvc.perform(get("/account/profile").session(login(reader)))
                .andExpect(status().isOk()).andExpect(view().name("account/profile"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(reader.readerNo())))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"contact\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }

    @Test
    void profileUpdatesAllowOnlyCurrentUsersEditableFieldsAndAreAudited() throws Exception {
        AppUser readerBefore = users.findById(reader.id()).orElseThrow();
        ReaderProfile profileBefore = readerProfiles.findByUserId(reader.id()).orElseThrow();
        AppUser adminBefore = users.findById(admin.id()).orElseThrow();
        MockHttpSession readerSession = login(reader);

        mvc.perform(post("/account/profile").with(csrf()).session(readerSession)
                        .param("displayName", "伪造姓名").param("contact", "伪造联系方式")
                        .param("role", "ADMIN").param("status", "DISABLED")
                        .param("readerNo", "FORGED-READER-NO").param("userId", admin.id().toString()))
                .andExpect(status().isOk()).andExpect(view().name("account/profile"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("只能提交本人允许修改的资料字段")));
        assertEquals(readerBefore.getDisplayName(), users.findById(reader.id()).orElseThrow().getDisplayName());
        assertEquals(profileBefore.getReaderNo(), readerProfiles.findByUserId(reader.id()).orElseThrow().getReaderNo());
        assertEquals(profileBefore.getContact(), readerProfiles.findByUserId(reader.id()).orElseThrow().getContact());
        assertEquals(adminBefore.getDisplayName(), users.findById(admin.id()).orElseThrow().getDisplayName());
        assertEquals(adminBefore.getRole(), users.findById(admin.id()).orElseThrow().getRole());
        assertEquals(adminBefore.getStatus(), users.findById(admin.id()).orElseThrow().getStatus());

        mvc.perform(post("/account/profile").with(csrf()).session(readerSession)
                        .param("displayName", "  新读者姓名  ").param("contact", "  新联系方式  "))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/account/profile?updated=true"));
        AppUser readerAfter = users.findById(reader.id()).orElseThrow();
        ReaderProfile profileAfter = readerProfiles.findByUserId(reader.id()).orElseThrow();
        assertEquals("新读者姓名", readerAfter.getDisplayName());
        assertEquals(UserRole.READER, readerAfter.getRole());
        assertEquals(UserStatus.ACTIVE, readerAfter.getStatus());
        assertEquals(reader.readerNo(), profileAfter.getReaderNo());
        assertEquals("新联系方式", profileAfter.getContact());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_user_id = ? " +
                        "AND action = 'OWN_PROFILE_UPDATED' AND target_type = 'USER' AND target_id = ?",
                Integer.class, reader.id(), reader.id()));
        String auditSummary = jdbc.queryForObject("SELECT summary FROM audit_log WHERE actor_user_id = ? " +
                "AND action = 'OWN_PROFILE_UPDATED' AND target_id = ? ORDER BY id DESC LIMIT 1",
                String.class, reader.id(), reader.id());
        assertFalse(auditSummary.contains("新联系方式"));

        MockHttpSession librarianSession = login(librarian);
        mvc.perform(post("/account/profile").with(csrf()).session(librarianSession)
                        .param("displayName", "不应更新").param("contact", "不允许的工作人员联系方式"))
                .andExpect(status().isOk()).andExpect(content().string(
                        org.hamcrest.Matchers.containsString("只能提交本人允许修改的资料字段")));
        assertEquals("工作人员原姓名", users.findById(librarian.id()).orElseThrow().getDisplayName());
        mvc.perform(post("/account/profile").with(csrf()).session(librarianSession)
                        .param("displayName", "工作人员新姓名"))
                .andExpect(status().is3xxRedirection());
        assertEquals("工作人员新姓名", users.findById(librarian.id()).orElseThrow().getDisplayName());
        assertEquals(UserRole.LIBRARIAN, users.findById(librarian.id()).orElseThrow().getRole());
        assertEquals(UserStatus.ACTIVE, users.findById(librarian.id()).orElseThrow().getStatus());
    }

    @Test
    void activePasswordChangeChecksOldAndNewPasswordsInvalidatesSessionAndAudits() throws Exception {
        MockHttpSession session = login(admin);
        AppUser before = users.findById(admin.id()).orElseThrow();
        long version = before.getCredentialVersion();
        String nextPassword = "brand-new-account-pass-456";

        mvc.perform(post("/account/password").with(csrf()).session(session)
                        .param("currentPassword", "wrong-current-pass")
                        .param("newPassword", nextPassword).param("confirmPassword", nextPassword))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("当前密码不正确")));
        mvc.perform(post("/account/password").with(csrf()).session(session)
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", "too-short").param("confirmPassword", "too-short"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("12—64")));
        mvc.perform(post("/account/password").session(session)
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", nextPassword).param("confirmPassword", nextPassword))
                .andExpect(status().isForbidden());
        assertEquals(version, users.findById(admin.id()).orElseThrow().getCredentialVersion());

        mvc.perform(post("/account/password").with(csrf()).session(session)
                        .param("currentPassword", PASSWORD)
                        .param("newPassword", nextPassword).param("confirmPassword", nextPassword))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?changed=true"));
        assertTrue(session.isInvalid());
        AppUser after = users.findById(admin.id()).orElseThrow();
        assertEquals(version + 1, after.getCredentialVersion());
        assertFalse(after.isMustChangePassword());
        assertTrue(passwordEncoder.matches(nextPassword, after.getPasswordHash()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_user_id = ? " +
                "AND action = 'OWN_PASSWORD_CHANGED' AND target_id = ?", Integer.class, admin.id(), admin.id()));
        mvc.perform(post("/login").with(csrf()).param("username", admin.loginName()).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error=true"));
        mvc.perform(post("/login").with(csrf()).param("username", admin.loginName()).param("password", nextPassword))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
    }

    @Test
    void everyRoleCompletesForcedPasswordChangeBeforeOpeningProfile() throws Exception {
        for (UserRole role : UserRole.values()) {
            String loginName = "pending_" + role.name().toLowerCase(Locale.ROOT) + "_" + token();
            AppUser pending = role == UserRole.READER
                    ? accounts.createReader(UserRole.ADMIN, loginName, PASSWORD, "待强制改密账号",
                    "PENDING-" + token(), null)
                    : accounts.createUser(loginName, PASSWORD, "待强制改密账号", role);
            userIds.add(pending.getId());
            long version = pending.getCredentialVersion();
            MockHttpSession session = login(new Fixture(pending.getId(), loginName, role, null));

            mvc.perform(get("/account/profile").session(session))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/account/password"));
            mvc.perform(post("/account/password").with(csrf()).session(session)
                            .param("currentPassword", PASSWORD)
                            .param("newPassword", "forced-new-pass-456")
                            .param("confirmPassword", "forced-new-pass-456"))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?changed=true"));
            assertTrue(session.isInvalid());
            AppUser changed = users.findById(pending.getId()).orElseThrow();
            assertFalse(changed.isMustChangePassword());
            assertEquals(version + 1, changed.getCredentialVersion());
            assertTrue(passwordEncoder.matches("forced-new-pass-456", changed.getPasswordHash()));
            mvc.perform(get("/account/profile").session(login(
                            new Fixture(pending.getId(), loginName, role, null), "forced-new-pass-456")))
                    .andExpect(status().isOk()).andExpect(view().name("account/profile"));
        }
    }

    private Fixture activeUser(UserRole role) {
        String loginName = "self_" + role.name().toLowerCase(Locale.ROOT) + "_" + token();
        AppUser user = accounts.createUser(loginName, PASSWORD,
                role == UserRole.LIBRARIAN ? "工作人员原姓名" : "管理员原姓名", role);
        user.markPasswordChanged();
        users.saveAndFlush(user);
        userIds.add(user.getId());
        return new Fixture(user.getId(), loginName, role, null);
    }

    private MockHttpSession login(Fixture fixture) throws Exception {
        return login(fixture, PASSWORD);
    }

    private MockHttpSession login(Fixture fixture, String password) throws Exception {
        var result = mvc.perform(post("/login").with(csrf())
                        .param("username", fixture.loginName().toUpperCase(Locale.ROOT)).param("password", password))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String token() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }

    private record Fixture(Long id, String loginName, UserRole role, String readerNo) {}
}
