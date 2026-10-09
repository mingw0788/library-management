package com.example.library.audit;

import com.example.library.account.AccountService;
import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.UserRole;
import com.example.library.catalog.Book;
import com.example.library.catalog.BookCopy;
import com.example.library.catalog.BookCopyService;
import com.example.library.catalog.BookService;
import com.example.library.catalog.Category;
import com.example.library.catalog.CategoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditLogTest {

    private static final String PASSWORD = "audit-test-password-123";

    @Autowired private MockMvc mvc;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private CategoryService categories;
    @Autowired private BookService books;
    @Autowired private BookCopyService copies;
    @MockitoSpyBean private AuditLogRepository auditRepositorySpy;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> bookIds = new ArrayList<>();
    private final List<Long> copyIds = new ArrayList<>();
    private final List<Long> loanIds = new ArrayList<>();
    private final List<Long> readerProfileUserIds = new ArrayList<>();
    private Fixture admin;
    private Fixture librarian;
    private Fixture reader;

    @BeforeEach
    void prepare() {
        // Remove only deterministic fixture rows left by this test if an earlier run stopped unexpectedly.
        jdbc.update("DELETE FROM audit_log WHERE action LIKE 'PAGE_EVENT_%'");
        admin = fixture(UserRole.ADMIN);
        librarian = fixture(UserRole.LIBRARIAN);
        reader = fixture(UserRole.READER);
    }

    @AfterEach
    void clean() {
        reset(auditRepositorySpy);
        loanIds.forEach(id -> jdbc.update("DELETE FROM loan WHERE id = ?", id));
        copyIds.forEach(id -> jdbc.update("DELETE FROM book_copy WHERE id = ?", id));
        bookIds.forEach(id -> jdbc.update("DELETE FROM book WHERE id = ?", id));
        categoryIds.forEach(id -> jdbc.update("DELETE FROM category WHERE id = ?", id));
        readerProfileUserIds.forEach(id -> jdbc.update("DELETE FROM reader_profile WHERE user_id = ?", id));
        userIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        users.flush();
        jdbc.update("DELETE FROM audit_log WHERE action LIKE 'PAGE_EVENT_%'");
        userIds.clear();
        categoryIds.clear();
        bookIds.clear();
        copyIds.clear();
        loanIds.clear();
        readerProfileUserIds.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulWriteStoresActorTargetAndSafeSummaryAndOnlyAdminCanRead() throws Exception {
        MockHttpSession librarianSession = login(librarian);
        String categoryName = "审计分类-" + token();
        Instant notBefore = Instant.now().minusSeconds(1);
        mvc.perform(post("/staff/categories").with(csrf()).session(librarianSession).param("name", categoryName))
                .andExpect(status().is3xxRedirection());
        Long categoryId = jdbc.queryForObject("SELECT id FROM category WHERE name = ?", Long.class, categoryName);
        categoryIds.add(categoryId);

        AuditRow row = jdbc.queryForObject("""
                SELECT actor_user_id, actor_login_snapshot, actor_role_snapshot, action, target_type,
                       target_id, result, summary, occurred_at
                FROM audit_log WHERE action = 'CATEGORY_CREATED' AND target_id = ?
                ORDER BY id DESC LIMIT 1
                """, (rs, index) -> new AuditRow(rs.getLong("actor_user_id"), rs.getString("actor_login_snapshot"),
                rs.getString("actor_role_snapshot"), rs.getString("action"), rs.getString("target_type"),
                rs.getLong("target_id"), rs.getString("result"), rs.getString("summary"),
                rs.getTimestamp("occurred_at").toInstant()), categoryId);

        assertEquals(librarian.id(), row.actorId());
        assertEquals(librarian.loginName(), row.actorLogin());
        assertEquals("LIBRARIAN", row.actorRole());
        assertEquals("CATEGORY_CREATED", row.action());
        assertEquals("CATEGORY", row.targetType());
        assertEquals(categoryId, row.targetId());
        assertEquals("SUCCESS", row.result());
        assertFalse(row.summary().contains(categoryName));
        assertNotNull(row.occurredAt());
        assertFalse(row.occurredAt().isBefore(notBefore));
        assertFalse(row.occurredAt().isAfter(Instant.now().plusSeconds(1)));

        mvc.perform(get("/admin/audit").session(login(reader))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/audit").session(login(librarian))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/audit").session(login(admin))).andExpect(status().isOk())
                .andExpect(view().name("audit/list"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("CATEGORY_CREATED")));
    }

    @Test
    void accountCheckoutAndReturnWritesHaveTheCorrectActorAndTarget() throws Exception {
        String staffLogin = "newstaff_" + token();
        mvc.perform(post("/admin/accounts").with(csrf()).session(login(admin))
                        .param("loginName", staffLogin).param("initialPassword", "staff-start-pass-456")
                        .param("displayName", "新建馆员").param("role", "LIBRARIAN"))
                .andExpect(status().is3xxRedirection());
        Long staffId = jdbc.queryForObject("SELECT id FROM app_user WHERE login_name = ?", Long.class, staffLogin);
        userIds.add(staffId);
        assertAuditRow("STAFF_CREATED", "USER", staffId, admin.id());

        MockHttpSession librarianSession = login(librarian);
        String readerLogin = "newreader_" + token();
        String readerNo = "RN-" + token();
        String contact = "contact-secret-" + token();
        mvc.perform(post("/staff/readers").with(csrf()).session(librarianSession)
                        .param("loginName", readerLogin).param("initialPassword", PASSWORD)
                        .param("displayName", "借还测试读者").param("readerNo", readerNo).param("contact", contact))
                .andExpect(status().is3xxRedirection());
        Long readerId = jdbc.queryForObject("SELECT id FROM app_user WHERE login_name = ?", Long.class, readerLogin);
        userIds.add(readerId);
        readerProfileUserIds.add(readerId);
        AuditRow readerCreated = findAudit("READER_CREATED", readerId);
        assertEquals(librarian.id(), readerCreated.actorId());
        assertFalse(readerCreated.summary().contains(contact));

        Category category = categories.create(UserRole.ADMIN, "审计借还分类-" + token());
        categoryIds.add(category.getId());
        Book book = books.create(UserRole.ADMIN, "审计借还书目-" + token(), "测试作者", null,
                null, null, category.getId(), null);
        bookIds.add(book.getId());
        BookCopy copy = copies.register(UserRole.ADMIN, book.getId(), "AUD-" + token(), null);
        copyIds.add(copy.getId());

        mvc.perform(post("/staff/loans/checkout").with(csrf()).session(librarianSession)
                        .param("readerNo", readerNo).param("barcode", copy.getBarcode()))
                .andExpect(status().is3xxRedirection());
        Long loanId = jdbc.queryForObject("SELECT id FROM loan WHERE copy_id = ? AND returned_at IS NULL",
                Long.class, copy.getId());
        loanIds.add(loanId);
        assertAuditRow("LOAN_CHECKED_OUT", "LOAN", loanId, librarian.id());

        mvc.perform(post("/staff/loans/return").with(csrf()).session(librarianSession)
                        .param("loanId", loanId.toString()))
                .andExpect(status().is3xxRedirection());
        assertAuditRow("LOAN_RETURNED", "LOAN", loanId, librarian.id());
    }

    @Test
    void transactionRollsBusinessWriteBackWhenAuditInsertFails() throws Exception {
        doThrow(new DataIntegrityViolationException("injected audit failure"))
                .when(auditRepositorySpy).saveAndFlush(any(AuditLog.class));
        String categoryName = "审计回滚-" + token();
        int auditCountBefore = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);

        mvc.perform(post("/staff/categories").with(csrf()).session(login(librarian)).param("name", categoryName))
                .andExpect(status().is3xxRedirection());

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE name = ?", Integer.class, categoryName));
        assertEquals(auditCountBefore, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class));
    }

    @Test
    void auditListUsesStableTwentyFiveRowPages() throws Exception {
        Instant base = Instant.now();
        for (int index = 1; index <= 27; index++) {
            auditLogs.saveAndFlush(new AuditLog(admin.id(), admin.loginName(), "ADMIN", "PAGE_EVENT_" + index,
                    "TEST", (long) index, "SUCCESS", "分页测试", base.plusSeconds(index)));
        }

        MockHttpSession session = login(admin);
        mvc.perform(get("/admin/audit").session(session))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("PAGE_EVENT_27")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PAGE_EVENT_1</td>"))));
        mvc.perform(get("/admin/audit").param("page", "2").session(session))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("PAGE_EVENT_1</td>")));
    }

    @Test
    void failedAuthenticationIsLoggedWithoutCredentials() throws Exception {
        String secret = "password-secret-" + token();
        Logger logger = (Logger) LoggerFactory.getLogger(
                com.example.library.security.SecurityConfiguration.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            mvc.perform(post("/login").with(csrf()).param("username", "audit-unknown").param("password", secret))
                    .andExpect(status().is3xxRedirection());
            String loggedEvents = events.list.toString();
            assertTrue(loggedEvents.contains("Authentication failure"));
            assertTrue(loggedEvents.contains("path=/login"));
            assertFalse(loggedEvents.contains(secret));
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    @Test
    void rejectedHttpRequestLogsStatusWithoutQueryValues() throws Exception {
        String sensitiveQuery = "token-secret-" + token();
        Logger logger = (Logger) LoggerFactory.getLogger(
                com.example.library.security.SafeRequestLoggingFilter.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            mvc.perform(get("/admin/audit").param("token", sensitiveQuery).session(login(reader)))
                    .andExpect(status().isForbidden());
            String loggedEvents = events.list.toString();
            assertTrue(loggedEvents.contains("Request processing failed"));
            assertTrue(loggedEvents.contains("method=GET"));
            assertTrue(loggedEvents.contains("path=/admin/audit"));
            assertTrue(loggedEvents.contains("errorType=AuthorizationDeniedException"));
            assertFalse(loggedEvents.contains(sensitiveQuery));
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }

    private Fixture fixture(UserRole role) {
        String loginName = "aud_" + token();
        AppUser user = accounts.createUser(loginName, PASSWORD, "审计测试账号", role);
        user.markPasswordChanged();
        users.saveAndFlush(user);
        userIds.add(user.getId());
        return new Fixture(user.getId(), loginName);
    }

    private MockHttpSession login(Fixture fixture) throws Exception {
        var result = mvc.perform(post("/login").with(csrf())
                        .param("username", fixture.loginName().toUpperCase(Locale.ROOT)).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void assertAuditRow(String action, String targetType, Long targetId, Long actorId) {
        AuditRow row = findAudit(action, targetId);
        assertEquals(actorId, row.actorId());
        assertEquals(targetType, row.targetType());
        assertEquals(targetId, row.targetId());
        assertEquals("SUCCESS", row.result());
        assertNotNull(row.occurredAt());
    }

    private AuditRow findAudit(String action, Long targetId) {
        return jdbc.queryForObject("""
                SELECT actor_user_id, actor_login_snapshot, actor_role_snapshot, action, target_type,
                       target_id, result, summary, occurred_at
                FROM audit_log WHERE action = ? AND target_id = ? ORDER BY id DESC LIMIT 1
                """, (rs, index) -> new AuditRow(rs.getLong("actor_user_id"), rs.getString("actor_login_snapshot"),
                rs.getString("actor_role_snapshot"), rs.getString("action"), rs.getString("target_type"),
                rs.getLong("target_id"), rs.getString("result"), rs.getString("summary"),
                rs.getTimestamp("occurred_at").toInstant()), action, targetId);
    }

    private static String token() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }

    private record Fixture(Long id, String loginName) {}

    private record AuditRow(Long actorId, String actorLogin, String actorRole, String action,
                            String targetType, Long targetId, String result, String summary, Instant occurredAt) {}
}
