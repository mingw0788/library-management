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
import com.example.library.catalog.BookStatus;
import com.example.library.catalog.Category;
import com.example.library.catalog.CategoryRepository;
import com.example.library.catalog.CategoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanCheckoutTest {

    private static final String PASSWORD = "checkout-test-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private LoanCheckoutService checkout;
    @Autowired private LoanRepository loans;
    @Autowired private LoanRules rules;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private ReaderProfileRepository readers;
    @Autowired private BookService bookService;
    @Autowired private BookRepository books;
    @Autowired private BookCopyService copyService;
    @Autowired private BookCopyRepository copies;
    @MockitoSpyBean private BookCopyRepository copyRepositorySpy;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;

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
        category = categoryService.create(UserRole.ADMIN, "T13 分类-" + token());
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
    void librarianCanCheckoutAndPrgShowsLoanNumberAndDueDate() throws Exception {
        BookCopy copy = copy("正常借出 " + token());
        MockHttpSession session = login(librarian);

        mvc.perform(get("/staff/loans/checkout").session(session))
                .andExpect(status().isOk()).andExpect(view().name("loan/checkout"))
                .andExpect(content().string(containsString("读者证号")));
        mvc.perform(post("/staff/loans/checkout").with(csrf()).session(session)
                        .param("readerNo", reader.getReaderNo()).param("barcode", copy.getBarcode()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/loans/checkout"))
                .andExpect(flash().attribute("notice", containsString("应还日期")));

        Loan loan = loans.findByReader_IdAndReturnedAtIsNull(reader.getId()).orElseThrow();
        assertEquals(copy.getId(), loan.getCopyId());
        assertEquals(librarian.getId(), loan.getCheckoutOperator().getId());
        assertEquals(rules.dueDateFor(loan.getBorrowedAt()), loan.getDueDate());
        assertEquals(BookCopyStatus.ON_LOAN, copies.findById(copy.getId()).orElseThrow().getStatus());
        assertEquals(BookStatus.ACTIVE, books.findById(copy.getBookId()).orElseThrow().getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> checkout.checkout(librarian.getId(), UserRole.LIBRARIAN, reader.getReaderNo(), copy.getBarcode()));
    }

    @Test
    void checkoutRejectsDisabledOverdueFullDuplicateTitleAndUnavailableCopy() {
        BookCopy firstCopy = copy("同书目规则 " + token());
        BookCopy secondCopy = copyFor(books.findById(firstCopy.getBookId()).orElseThrow(), "同书目另一册");
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN, reader.getReaderNo(), firstCopy.getBarcode());
        assertThrows(IllegalArgumentException.class, () -> checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader.getReaderNo(), secondCopy.getBarcode()));
        assertThrows(IllegalArgumentException.class, () -> checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader.getReaderNo(), firstCopy.getBarcode()));

        ReaderProfile disabled = reader();
        accounts.disableReader(UserRole.ADMIN, disabled.getUserId());
        BookCopy disabledCopy = copy("禁用读者拒绝 " + token());
        assertThrows(IllegalArgumentException.class, () -> checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                disabled.getReaderNo(), disabledCopy.getBarcode()));

        ReaderProfile overdue = reader();
        BookCopy oldLoan = copy("逾期旧借阅 " + token());
        addOpenLoan(overdue, oldLoan, rules.today().minusDays(1));
        BookCopy newCopy = copy("逾期拒绝新借 " + token());
        assertThrows(IllegalArgumentException.class, () -> checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                overdue.getReaderNo(), newCopy.getBarcode()));

        ReaderProfile full = reader();
        for (int index = 0; index < 5; index++) {
            BookCopy active = copy("已借副本 " + index + " " + token());
            addOpenLoan(full, active, rules.today().plusDays(14));
        }
        BookCopy sixth = copy("第六册拒绝 " + token());
        assertThrows(IllegalArgumentException.class, () -> checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                full.getReaderNo(), sixth.getBarcode()));

        BookCopy withdrawn = copy("注销副本拒绝 " + token());
        copyService.withdraw(UserRole.ADMIN, withdrawn.getId());
        ReaderProfile eligible = reader();
        assertThrows(IllegalArgumentException.class, () -> checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                eligible.getReaderNo(), withdrawn.getBarcode()));
    }

    @Test
    void onlyStaffCanCheckoutAndCsrfIsRequired() throws Exception {
        BookCopy copy = copy("权限测试 " + token());
        MockHttpSession readerSession = login(reader.getUserId());
        mvc.perform(get("/staff/loans/checkout").session(readerSession)).andExpect(status().isForbidden());
        mvc.perform(post("/staff/loans/checkout").with(csrf()).session(readerSession)
                        .param("readerNo", reader.getReaderNo()).param("barcode", copy.getBarcode()))
                .andExpect(status().isForbidden());

        MockHttpSession staffSession = login(librarian);
        mvc.perform(post("/staff/loans/checkout").session(staffSession)
                        .param("readerNo", reader.getReaderNo()).param("barcode", copy.getBarcode()))
                .andExpect(status().isForbidden());
        assertTrue(loans.findByReader_IdAndReturnedAtIsNull(reader.getId()).isEmpty());
        assertEquals(BookCopyStatus.AVAILABLE, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void checkoutSerializesTwoReadersCompetingForTheSameCopy() throws Exception {
        BookCopy copy = copy("同副本并发 " + token());
        ReaderProfile anotherReader = reader();

        List<Boolean> results = race(
                () -> checkout.checkout(librarian.getId(), UserRole.LIBRARIAN, reader.getReaderNo(), copy.getBarcode()),
                () -> checkout.checkout(secondLibrarian.getId(), UserRole.LIBRARIAN,
                        anotherReader.getReaderNo(), copy.getBarcode()));

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE copy_id = ? AND returned_at IS NULL",
                Integer.class, copy.getId()));
        assertEquals(BookCopyStatus.ON_LOAN, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void checkoutSerializesTwoRequestsForReadersLastAvailableSlot() throws Exception {
        ReaderProfile full = reader();
        for (int index = 0; index < 4; index++) {
            BookCopy active = copy("并发前已有 " + index + " " + token());
            addOpenLoan(full, active, rules.today().plusDays(14));
        }
        BookCopy candidateOne = copy("并发候选一 " + token());
        BookCopy candidateTwo = copy("并发候选二 " + token());

        List<Boolean> results = race(
                () -> checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                        full.getReaderNo(), candidateOne.getBarcode()),
                () -> checkout.checkout(secondLibrarian.getId(), UserRole.LIBRARIAN,
                        full.getReaderNo(), candidateTwo.getBarcode()));

        assertEquals(1, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(5, loans.countByReader_IdAndReturnedAtIsNull(full.getId()));
        assertEquals(1, List.of(candidateOne, candidateTwo).stream()
                .filter(copy -> copies.findById(copy.getId()).orElseThrow().getStatus() == BookCopyStatus.ON_LOAN).count());
    }

    @Test
    void activeLoanPreventsCopyWithdrawalAndBookArchive() {
        BookCopy copy = copy("借出后保护 " + token());
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN, reader.getReaderNo(), copy.getBarcode());
        assertThrows(IllegalArgumentException.class, () -> copyService.withdraw(UserRole.ADMIN, copy.getId()));
        assertThrows(IllegalArgumentException.class, () -> bookService.archive(UserRole.ADMIN, copy.getBookId()));
        assertEquals(BookCopyStatus.ON_LOAN, copies.findById(copy.getId()).orElseThrow().getStatus());
        assertEquals(BookStatus.ACTIVE, books.findById(copy.getBookId()).orElseThrow().getStatus());
    }

    @Test
    void databaseFailureAfterLoanInsertRollsBackBothLoanAndCopyStatus() {
        BookCopy copy = copy("借出故障回滚 " + token());
        doThrow(new org.springframework.dao.DataIntegrityViolationException("模拟数据库写入失败"))
                .when(copyRepositorySpy).saveAndFlush(any(BookCopy.class));

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                        reader.getReaderNo(), copy.getBarcode()));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE copy_id = ?", Integer.class, copy.getId()));
        assertEquals(BookCopyStatus.AVAILABLE, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    private List<Boolean> race(CheckoutCall first, CheckoutCall second) throws Exception {
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

    private static boolean invokeRace(CheckoutCall call, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试启动超时");
        try {
            call.run();
            return true;
        } catch (IllegalArgumentException expectedCompetition) {
            return false;
        }
    }

    private void addOpenLoan(ReaderProfile borrower, BookCopy copy, java.time.LocalDate dueDate) {
        Instant borrowedAt = rules.today().minusDays(1).atStartOfDay(rules.policy().zoneId()).toInstant();
        loans.saveAndFlush(new Loan(borrower, copy, borrowedAt, dueDate, librarian));
        jdbc.update("UPDATE book_copy SET status = 'ON_LOAN' WHERE id = ?", copy.getId());
    }

    private ReaderProfile reader() {
        String suffix = token();
        AppUser user = accounts.createReader(UserRole.ADMIN, "checkout_reader_" + suffix, PASSWORD,
                "借出测试读者", "CHECKOUT-" + suffix, null);
        userIds.add(user.getId());
        ReaderProfile profile = readers.findByUserId(user.getId()).orElseThrow();
        profileIds.add(profile.getId());
        return profile;
    }

    private AppUser staff(UserRole role) {
        AppUser user = accounts.createUser("checkout_staff_" + token(), PASSWORD, "借出测试员工", role);
        user.markPasswordChanged();
        userIds.add(users.saveAndFlush(user).getId());
        return user;
    }

    private BookCopy copy(String title) { return copyFor(book(title), title); }

    private BookCopy copyFor(Book book, String label) {
        BookCopy copy = copyService.register(UserRole.ADMIN, book.getId(), "CHECKOUT-" + token(), label);
        copyIds.add(copy.getId());
        return copy;
    }

    private Book book(String title) {
        Book book = bookService.create(UserRole.ADMIN, title, "借出测试作者", null, null, null, category.getId(), null);
        bookIds.add(book.getId());
        return book;
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
    @FunctionalInterface private interface CheckoutCall { void run(); }
}
