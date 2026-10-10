package com.example.library.loan;

import com.example.library.account.AccountService;
import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.ReaderProfile;
import com.example.library.account.ReaderProfileRepository;
import com.example.library.account.UserRole;
import com.example.library.catalog.Book;
import com.example.library.catalog.BookCopy;
import com.example.library.catalog.BookCopyRepository;
import com.example.library.catalog.BookCopyService;
import com.example.library.catalog.BookCopyStatus;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanReturnTest {

    private static final String PASSWORD = "return-test-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private LoanCheckoutService checkout;
    @Autowired private LoanReturnService returns;
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
    @MockitoSpyBean private BookCopyRepository copyRepositorySpy;

    private final List<Long> profileIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> copyIds = new ArrayList<>();
    private final List<Long> bookIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private Category category;
    private AppUser admin;
    private AppUser librarian;
    private AppUser secondLibrarian;
    private ReaderProfile reader;

    @BeforeEach
    void prepare() {
        category = categoryService.create(UserRole.ADMIN, "T14 分类-" + token());
        categoryIds.add(category.getId());
        admin = staff(UserRole.ADMIN);
        librarian = staff(UserRole.LIBRARIAN);
        secondLibrarian = staff(UserRole.LIBRARIAN);
        reader = reader();
    }

    @AfterEach
    void clean() {
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
    void returnByLoanNumberAndBarcodeRestoresCopyAndAllowsReborrow() {
        BookCopy copy = copy("归还后重借 " + token());
        Loan firstLoan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader.getReaderNo(), copy.getBarcode());

        Loan returned = returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, firstLoan.getId());
        assertEquals(librarian.getId(), returned.getReturnOperator().getId());
        assertNotNull(returned.getReturnedAt());
        assertFalse(returned.getReturnedAt().isBefore(returned.getBorrowedAt()));
        assertEquals(BookCopyStatus.AVAILABLE, copies.findById(copy.getId()).orElseThrow().getStatus());

        Loan secondLoan = checkout.checkout(secondLibrarian.getId(), UserRole.LIBRARIAN,
                reader.getReaderNo(), copy.getBarcode());
        Loan secondReturn = returns.returnByBarcode(admin.getId(), UserRole.ADMIN, copy.getBarcode());
        assertEquals(secondLoan.getId(), secondReturn.getId());
        assertNotNull(secondReturn.getReturnedAt());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE copy_id = ?", Integer.class, copy.getId()));
        assertEquals(BookCopyStatus.AVAILABLE, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void disabledReaderCanReturnAnExistingLoan() {
        BookCopy copy = copy("禁用读者归还 " + token());
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader.getReaderNo(), copy.getBarcode());
        accounts.disableReader(UserRole.ADMIN, reader.getUserId());

        Loan returned = returns.returnByBarcode(admin.getId(), UserRole.ADMIN, copy.getBarcode());

        assertEquals(loan.getId(), returned.getId());
        assertEquals(admin.getId(), returned.getReturnOperator().getId());
        assertEquals(BookCopyStatus.AVAILABLE, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void invalidAndRepeatedReturnRequestsDoNotChangeCopyAgain() {
        BookCopy copy = copy("重复归还 " + token());
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader.getReaderNo(), copy.getBarcode());
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, loan.getId());
        long versionAfterReturn = jdbc.queryForObject("SELECT version FROM loan WHERE id = ?", Long.class, loan.getId());
        long copyVersionAfterReturn = copies.findById(copy.getId()).orElseThrow().getVersion();

        IllegalArgumentException repeated = assertThrows(IllegalArgumentException.class,
                () -> returns.returnByLoanId(secondLibrarian.getId(), UserRole.LIBRARIAN, loan.getId()));
        assertTrue(repeated.getMessage().contains("已经归还"));
        assertThrows(IllegalArgumentException.class,
                () -> returns.returnByBarcode(librarian.getId(), UserRole.LIBRARIAN, copy.getBarcode()));
        assertThrows(IllegalArgumentException.class,
                () -> returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, -10L));
        assertThrows(IllegalArgumentException.class,
                () -> returns.returnByBarcode(librarian.getId(), UserRole.LIBRARIAN, "UNKNOWN-" + token()));

        assertEquals(versionAfterReturn, jdbc.queryForObject("SELECT version FROM loan WHERE id = ?", Long.class, loan.getId()));
        assertEquals(copyVersionAfterReturn, copies.findById(copy.getId()).orElseThrow().getVersion());
        assertEquals(BookCopyStatus.AVAILABLE, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void concurrentReturnsOfTheSameLoanOnlyRecordOneReturn() throws Exception {
        BookCopy copy = copy("并发归还 " + token());
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader.getReaderNo(), copy.getBarcode());

        List<Boolean> results = race(
                () -> returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, loan.getId()),
                () -> returns.returnByLoanId(secondLibrarian.getId(), UserRole.LIBRARIAN, loan.getId()));

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE id = ? AND returned_at IS NOT NULL",
                Integer.class, loan.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE id = ? AND return_operator_id IS NOT NULL",
                Integer.class, loan.getId()));
        assertEquals(BookCopyStatus.AVAILABLE, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void concurrentReturnAndCheckoutOfSameCopyKeepLoanAndInventoryConsistent() throws Exception {
        BookCopy copy = copy("借还交错 " + token());
        Loan originalLoan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader.getReaderNo(), copy.getBarcode());
        ReaderProfile nextReader = reader();

        List<Boolean> outcomes = race(
                () -> returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, originalLoan.getId()),
                () -> checkout.checkout(secondLibrarian.getId(), UserRole.LIBRARIAN,
                        nextReader.getReaderNo(), copy.getBarcode()));

        assertTrue(outcomes.get(0), "并发借出不能阻止原借阅完成归还");
        boolean checkedOutAgain = outcomes.get(1);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE id = ? AND returned_at IS NOT NULL " +
                "AND return_operator_id = ?", Integer.class, originalLoan.getId(), librarian.getId()));
        assertEquals(checkedOutAgain ? 1 : 0,
                jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE copy_id = ? AND returned_at IS NULL",
                        Integer.class, copy.getId()));
        assertEquals(checkedOutAgain ? 2 : 1,
                jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE copy_id = ?", Integer.class, copy.getId()));
        assertEquals(checkedOutAgain ? BookCopyStatus.ON_LOAN : BookCopyStatus.AVAILABLE,
                copies.findById(copy.getId()).orElseThrow().getStatus());
        if (checkedOutAgain) {
            assertEquals(nextReader.getId(), jdbc.queryForObject(
                    "SELECT reader_id FROM loan WHERE copy_id = ? AND returned_at IS NULL", Long.class, copy.getId()));
        }
    }

    @Test
    void failedCopyUpdateRollsBackReturnTimestampAndOperator() {
        BookCopy copy = copy("归还故障回滚 " + token());
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader.getReaderNo(), copy.getBarcode());
        doThrow(new DataIntegrityViolationException("模拟副本写入失败"))
                .when(copyRepositorySpy).saveAndFlush(any(BookCopy.class));

        assertThrows(DataIntegrityViolationException.class,
                () -> returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, loan.getId()));

        assertNull(jdbc.queryForObject("SELECT returned_at FROM loan WHERE id = ?", java.sql.Timestamp.class, loan.getId()));
        assertNull(jdbc.queryForObject("SELECT return_operator_id FROM loan WHERE id = ?", Long.class, loan.getId()));
        assertEquals(BookCopyStatus.ON_LOAN, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void staffReturnPageSupportsCsrfAndRejectsReaders() throws Exception {
        BookCopy copy = copy("网页归还 " + token());
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader.getReaderNo(), copy.getBarcode());

        MockHttpSession staffSession = login(librarian);
        mvc.perform(get("/staff/loans/return").session(staffSession))
                .andExpect(status().isOk()).andExpect(view().name("loan/return"))
                .andExpect(content().string(containsString("借阅编号")));
        assertNull(jdbc.queryForObject("SELECT returned_at FROM loan WHERE id = ?", java.sql.Timestamp.class, loan.getId()),
                "GET 归还页面不能结束借阅");
        assertEquals(BookCopyStatus.ON_LOAN, copies.findById(copy.getId()).orElseThrow().getStatus());
        mvc.perform(post("/staff/loans/return").session(staffSession).param("loanId", loan.getId().toString()))
                .andExpect(status().isForbidden());
        assertNull(jdbc.queryForObject("SELECT returned_at FROM loan WHERE id = ?", java.sql.Timestamp.class, loan.getId()),
                "缺少 CSRF 令牌时不得归还");
        assertEquals(BookCopyStatus.ON_LOAN, copies.findById(copy.getId()).orElseThrow().getStatus());
        mvc.perform(post("/staff/loans/return").with(csrf()).session(staffSession)
                        .param("loanId", loan.getId().toString()).param("barcode", copy.getBarcode()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/loans/return"))
                .andExpect(flash().attribute("error", containsString("只填写一个")));
        mvc.perform(post("/staff/loans/return").with(csrf()).session(staffSession)
                        .param("barcode", copy.getBarcode()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("notice", containsString("副本状态已更新")))
                .andExpect(flash().attribute("notice", containsString("预约读者保留")));

        MockHttpSession readerSession = login(reader.getUserId());
        mvc.perform(get("/staff/loans/return").session(readerSession)).andExpect(status().isForbidden());
        mvc.perform(post("/staff/loans/return").with(csrf()).session(readerSession).param("loanId", "1"))
                .andExpect(status().isForbidden());
        assertEquals(BookCopyStatus.AVAILABLE, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    private List<Boolean> race(ReturnCall first, ReturnCall second) throws Exception {
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

    private static boolean invokeRace(ReturnCall call, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试启动超时");
        try {
            call.run();
            return true;
        } catch (IllegalArgumentException expectedCompetition) {
            return false;
        }
    }

    private ReaderProfile reader() {
        String suffix = token();
        AppUser user = accounts.createReader(UserRole.ADMIN, "return_reader_" + suffix, PASSWORD,
                "归还测试读者", "RETURN-" + suffix, null);
        userIds.add(user.getId());
        ReaderProfile profile = readers.findByUserId(user.getId()).orElseThrow();
        profileIds.add(profile.getId());
        return profile;
    }

    private AppUser staff(UserRole role) {
        AppUser user = accounts.createUser("return_staff_" + token(), PASSWORD, "归还测试员工", role);
        user.markPasswordChanged();
        userIds.add(users.saveAndFlush(user).getId());
        return user;
    }

    private BookCopy copy(String title) {
        Book book = bookService.create(UserRole.ADMIN, title, "归还测试作者", null, null,
                null, category.getId(), null);
        bookIds.add(book.getId());
        BookCopy copy = copyService.register(UserRole.ADMIN, book.getId(), "RETURN-" + token(), null);
        copyIds.add(copy.getId());
        return copy;
    }

    private MockHttpSession login(AppUser user) throws Exception { return login(user.getId()); }

    private MockHttpSession login(Long userId) throws Exception {
        AppUser user = users.findById(userId).orElseThrow();
        var result = mvc.perform(post("/login").with(csrf())
                        .param("username", user.getLoginName().toUpperCase(Locale.ROOT)).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String token() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    @FunctionalInterface private interface ReturnCall { void run(); }
}
