package com.example.library.loan;

import com.example.library.account.AccountService;
import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.ReaderProfile;
import com.example.library.account.ReaderProfileRepository;
import com.example.library.account.UserRole;
import com.example.library.audit.AuditLogService;
import com.example.library.catalog.Book;
import com.example.library.catalog.BookCopy;
import com.example.library.catalog.BookCopyRepository;
import com.example.library.catalog.BookCopyService;
import com.example.library.catalog.BookRepository;
import com.example.library.catalog.BookService;
import com.example.library.catalog.Category;
import com.example.library.catalog.CategoryRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanRenewalTest {

    private static final String PASSWORD = "renewal-test-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private LoanCheckoutService checkout;
    @Autowired private LoanReturnService returns;
    @Autowired private LoanRenewalService renewals;
    @Autowired private LoanRepository loans;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private ReaderProfileRepository readers;
    @Autowired private BookService bookService;
    @Autowired private BookRepository books;
    @Autowired private BookCopyService copyService;
    @Autowired private BookCopyRepository copies;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private AuditLogService auditLogs;

    private final List<Long> profileIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> copyIds = new ArrayList<>();
    private final List<Long> bookIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private Category category;
    private AppUser librarian;
    private ReaderProfile reader;

    @BeforeEach
    void prepare() {
        category = categoryService.create(UserRole.ADMIN, "X01 分类-" + token());
        categoryIds.add(category.getId());
        librarian = staff();
        reader = reader();
    }

    @AfterEach
    void clean() {
        userIds.forEach(id -> jdbc.update("DELETE FROM audit_log WHERE actor_user_id = ?", id));
        profileIds.forEach(id -> jdbc.update("DELETE FROM loan WHERE reader_id = ?", id));
        copyIds.forEach(id -> copies.findById(id).ifPresent(copies::delete));
        copies.flush();
        bookIds.forEach(id -> books.findById(id).ifPresent(books::delete));
        books.flush();
        categoryIds.forEach(id -> categories.findById(id).ifPresent(categories::delete));
        categories.flush();
        profileIds.forEach(id -> readers.findById(id).ifPresent(readers::delete));
        readers.flush();
        userIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        profileIds.clear();
        userIds.clear();
        copyIds.clear();
        bookIds.clear();
        categoryIds.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void eligibleReaderCanRenewOnceByFourteenDaysAtTheDueDateBoundary() {
        Loan loan = loanFor(reader);
        jdbc.update("UPDATE loan SET due_date = ? WHERE id = ?", Date.valueOf(rulesToday()), loan.getId());

        Loan renewed = renewals.renewForReader(reader.getUserId(), loan.getId());

        assertEquals(rulesToday().plusDays(14), renewed.getDueDate());
        assertEquals(1, renewed.getRenewCount());
        assertEquals(1, jdbc.queryForObject("SELECT renew_count FROM loan WHERE id = ?", Integer.class, loan.getId()));
        assertThrows(IllegalArgumentException.class,
                () -> renewals.renewForReader(reader.getUserId(), loan.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT renew_count FROM loan WHERE id = ?", Integer.class, loan.getId()));
    }

    @Test
    void rejectsOverdueReturnedDisabledOtherReadersAndReadersWithAnotherOverdueLoan() throws Exception {
        Loan overdue = loanFor(reader);
        jdbc.update("UPDATE loan SET due_date = ? WHERE id = ?", Date.valueOf(rulesToday().minusDays(1)), overdue.getId());
        assertThrows(IllegalArgumentException.class,
                () -> renewals.renewForReader(reader.getUserId(), overdue.getId()));
        jdbc.update("UPDATE loan SET due_date = ? WHERE id = ?", Date.valueOf(rulesToday().plusDays(14)), overdue.getId());

        ReaderProfile otherReader = reader();
        Loan anotherPersonsLoan = loanFor(reader);
        assertThrows(AccessDeniedException.class,
                () -> renewals.renewForReader(otherReader.getUserId(), anotherPersonsLoan.getId()));

        Loan withOtherOverdue = loanFor(reader);
        Loan otherOpenLoan = loanFor(reader);
        jdbc.update("UPDATE loan SET due_date = ? WHERE id = ?",
                Date.valueOf(rulesToday().minusDays(1)), otherOpenLoan.getId());
        assertThrows(IllegalArgumentException.class,
                () -> renewals.renewForReader(reader.getUserId(), withOtherOverdue.getId()));
        mvc.perform(get("/reader/loans").session(login(reader.getUserId())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("续借 14 天"))));
        jdbc.update("UPDATE loan SET due_date = ? WHERE id = ?",
                Date.valueOf(rulesToday().plusDays(14)), otherOpenLoan.getId());

        Loan disabled = loanFor(otherReader);
        accounts.disableReader(UserRole.ADMIN, otherReader.getUserId());
        assertThrows(IllegalArgumentException.class,
                () -> renewals.renewForReader(otherReader.getUserId(), disabled.getId()));

        Loan returned = loanFor(reader);
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, returned.getId());
        assertThrows(IllegalArgumentException.class,
                () -> renewals.renewForReader(reader.getUserId(), returned.getId()));
    }

    @Test
    void concurrentRequestsForOneLoanOnlyExtendItOnce() throws Exception {
        Loan loan = loanFor(reader);

        List<Boolean> outcomes = race(
                () -> renewals.renewForReader(reader.getUserId(), loan.getId()),
                () -> renewals.renewForReader(reader.getUserId(), loan.getId()));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, jdbc.queryForObject("SELECT renew_count FROM loan WHERE id = ?", Integer.class, loan.getId()));
        assertEquals(loan.getDueDate().plusDays(14), jdbc.queryForObject(
                "SELECT due_date FROM loan WHERE id = ?", Date.class, loan.getId()).toLocalDate());
    }

    @Test
    void readerPageRequiresCsrfEnforcesOwnershipAndAuditsSuccessfulRenewal() throws Exception {
        Loan loan = loanFor(reader);
        MockHttpSession readerSession = login(reader.getUserId());

        mvc.perform(get("/reader/loans").session(readerSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString("续借 14 天")));
        mvc.perform(post("/reader/loans/{id}/renew", loan.getId()).session(readerSession))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("SELECT renew_count FROM loan WHERE id = ?", Integer.class, loan.getId()));

        ReaderProfile otherReader = reader();
        MockHttpSession otherSession = login(otherReader.getUserId());
        mvc.perform(post("/reader/loans/{id}/renew", loan.getId()).with(csrf()).session(otherSession))
                .andExpect(status().isForbidden());
        assertEquals(0, jdbc.queryForObject("SELECT renew_count FROM loan WHERE id = ?", Integer.class, loan.getId()));

        MockHttpSession librarianSession = login(librarian.getId());
        mvc.perform(post("/reader/loans/{id}/renew", loan.getId()).with(csrf()).session(librarianSession))
                .andExpect(status().isForbidden());

        mvc.perform(post("/reader/loans/{id}/renew", loan.getId()).with(csrf()).session(readerSession))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/reader/loans"))
                .andExpect(flash().attribute("notice", containsString("新的应还日期")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_user_id = ? " +
                "AND action = 'LOAN_RENEWED' AND target_type = 'LOAN' AND target_id = ?",
                Integer.class, reader.getUserId(), loan.getId()));
        mvc.perform(get("/reader/loans").session(readerSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString("已续借")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("续借 14 天"))));
    }

    @Test
    void auditFailureRollsBackTheDueDateAndRenewCount() throws Exception {
        Loan loan = loanFor(reader);
        MockHttpSession readerSession = login(reader.getUserId());
        doThrow(new DataIntegrityViolationException("模拟审计写入失败"))
                .when(auditLogs).recordCurrent(reader.getUserId(), UserRole.READER, "LOAN_RENEWED", "LOAN",
                        loan.getId(), "读者续借14天");

        mvc.perform(post("/reader/loans/{id}/renew", loan.getId()).with(csrf()).session(readerSession))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("error", containsString("资料已发生变化")));

        assertEquals(0, jdbc.queryForObject("SELECT renew_count FROM loan WHERE id = ?", Integer.class, loan.getId()));
        assertEquals(loan.getDueDate(), jdbc.queryForObject("SELECT due_date FROM loan WHERE id = ?", Date.class,
                loan.getId()).toLocalDate());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'LOAN_RENEWED' " +
                "AND target_id = ?", Integer.class, loan.getId()));
    }

    private List<Boolean> race(RenewalCall first, RenewalCall second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> left = pool.submit(() -> invokeRace(first, ready, start));
            Future<Boolean> right = pool.submit(() -> invokeRace(second, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static boolean invokeRace(RenewalCall call, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试启动超时");
        try {
            call.run();
            return true;
        } catch (IllegalArgumentException expectedCompetition) {
            return false;
        }
    }

    private Loan loanFor(ReaderProfile borrower) {
        Book book = book("续借测试书目 " + token());
        BookCopy copy = copy(book);
        return checkout.checkout(librarian.getId(), UserRole.LIBRARIAN, borrower.getReaderNo(), copy.getBarcode());
    }

    private ReaderProfile reader() {
        String suffix = token();
        AppUser user = accounts.createReader(UserRole.ADMIN, "renew_reader_" + suffix, PASSWORD,
                "续借测试读者", "RENEW-" + suffix, null);
        user.markPasswordChanged();
        userIds.add(users.saveAndFlush(user).getId());
        ReaderProfile profile = readers.findByUserId(user.getId()).orElseThrow();
        profileIds.add(profile.getId());
        return profile;
    }

    private AppUser staff() {
        AppUser user = accounts.createUser("renew_staff_" + token(), PASSWORD, "续借测试馆员", UserRole.LIBRARIAN);
        user.markPasswordChanged();
        userIds.add(users.saveAndFlush(user).getId());
        return user;
    }

    private Book book(String title) {
        Book book = bookService.create(UserRole.ADMIN, title, "续借测试作者", null, null,
                null, category.getId(), null);
        bookIds.add(book.getId());
        return book;
    }

    private BookCopy copy(Book book) {
        BookCopy copy = copyService.register(UserRole.ADMIN, book.getId(), "RENEW-" + token(), null);
        copyIds.add(copy.getId());
        return copy;
    }

    private MockHttpSession login(Long userId) throws Exception {
        AppUser user = users.findById(userId).orElseThrow();
        var result = mvc.perform(post("/login").with(csrf())
                        .param("username", user.getLoginName().toUpperCase(Locale.ROOT)).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private java.time.LocalDate rulesToday() {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
    }

    private static String token() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }

    @FunctionalInterface private interface RenewalCall { void run(); }
}
