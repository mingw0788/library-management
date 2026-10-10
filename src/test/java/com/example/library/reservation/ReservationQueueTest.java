package com.example.library.reservation;

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
import com.example.library.loan.Loan;
import com.example.library.loan.LoanCheckoutService;
import com.example.library.loan.LoanRules;
import com.example.library.loan.LoanRepository;
import com.example.library.loan.LoanReturnService;
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
import java.sql.Timestamp;
import java.time.Clock;
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
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationQueueTest {

    private static final String PASSWORD = "reservation-test-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private ReservationService reservations;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private LoanCheckoutService checkout;
    @Autowired private LoanReturnService returns;
    @Autowired private LoanRepository loans;
    @Autowired private LoanRules loanRules;
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
    @Autowired private Clock circulationClock;
    @MockitoSpyBean private AuditLogService auditLogs;

    private final List<Long> profileIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> copyIds = new ArrayList<>();
    private final List<Long> bookIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private Category category;
    private AppUser librarian;

    @BeforeEach
    void prepare() {
        category = categoryService.create(UserRole.ADMIN, "X02 分类-" + token());
        categoryIds.add(category.getId());
        librarian = staff();
    }

    @AfterEach
    void clean() {
        userIds.forEach(id -> jdbc.update("DELETE FROM audit_log WHERE actor_user_id = ?", id));
        profileIds.forEach(id -> jdbc.update("DELETE FROM reservation WHERE reader_id = ?", id));
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
    void queuesByCreationTimeAndIdAndShowsOnlyEachReadersPosition() {
        ReaderFixture firstReader = reader();
        ReaderFixture secondReader = reader();
        ReaderFixture currentBorrower = reader();
        Book book = book("排队顺序测试 " + token());
        BookCopy copy = copy(book);
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                currentBorrower.profile().getReaderNo(), copy.getBarcode());

        Reservation first = reservations.createForReader(firstReader.account().getId(), book.getId());
        Reservation second = reservations.createForReader(secondReader.account().getId(), book.getId());

        List<Reservation> queue = reservationRepository.findByBookIdAndStatusOrderByCreatedAtAscIdAsc(
                book.getId(), ReservationStatus.PENDING);
        assertEquals(List.of(first.getId(), second.getId()), queue.stream().map(Reservation::getId).toList());
        assertEquals(1L, reservations.listMine(firstReader.account().getId()).get(0).queuePosition());
        assertEquals(2L, reservations.listMine(secondReader.account().getId()).get(0).queuePosition());
        assertTrue(reservations.listMine(currentBorrower.account().getId()).isEmpty());

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO reservation (reader_id, book_id, status, created_at, version) " +
                        "VALUES (?, ?, 'PENDING', ?, 0)", firstReader.profile().getId(), book.getId(),
                Timestamp.from(circulationClock.instant())));
    }

    @Test
    void rejectsUnavailableReservationTargetsAndIneligibleReaders() {
        ReaderFixture requester = reader();
        ReaderFixture borrower = reader();

        Book noCopies = book("无副本书目 " + token());
        assertThrows(IllegalArgumentException.class,
                () -> reservations.createForReader(requester.account().getId(), noCopies.getId()));

        Book availableBook = book("仍有可借副本 " + token());
        copy(availableBook);
        assertThrows(IllegalArgumentException.class,
                () -> reservations.createForReader(requester.account().getId(), availableBook.getId()));

        Book withdrawnBook = book("副本已注销 " + token());
        BookCopy withdrawnCopy = copy(withdrawnBook);
        copyService.withdraw(UserRole.ADMIN, withdrawnCopy.getId());
        assertThrows(IllegalArgumentException.class,
                () -> reservations.createForReader(requester.account().getId(), withdrawnBook.getId()));

        Book archivedBook = book("已归档书目 " + token());
        BookCopy archivedCopy = copy(archivedBook);
        copyService.withdraw(UserRole.ADMIN, archivedCopy.getId());
        bookService.archive(UserRole.ADMIN, archivedBook.getId());
        assertThrows(IllegalArgumentException.class,
                () -> reservations.createForReader(requester.account().getId(), archivedBook.getId()));

        Book target = book("读者资格目标 " + token());
        BookCopy targetCopy = copy(target);
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), targetCopy.getBarcode());

        Book overdueBook = book("逾期借阅书目 " + token());
        BookCopy overdueCopy = copy(overdueBook);
        Loan overdueLoan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                requester.profile().getReaderNo(), overdueCopy.getBarcode());
        jdbc.update("UPDATE loan SET due_date = ? WHERE id = ?", Date.valueOf(loanRules.today().minusDays(1)),
                overdueLoan.getId());
        assertThrows(IllegalArgumentException.class,
                () -> reservations.createForReader(requester.account().getId(), target.getId()));

        jdbc.update("UPDATE loan SET due_date = ? WHERE id = ?", Date.valueOf(loanRules.today().plusDays(14)),
                overdueLoan.getId());
        Book sameBook = book("本人已借书目 " + token());
        BookCopy sameBookCopy = copy(sameBook);
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                requester.profile().getReaderNo(), sameBookCopy.getBarcode());
        assertThrows(IllegalArgumentException.class,
                () -> reservations.createForReader(requester.account().getId(), sameBook.getId()));

        ReaderFixture atLimit = reader();
        for (int i = 0; i < loanRules.policy().maximumActiveLoans(); i++) {
            Book activeBook = book("达到借阅上限 " + token());
            BookCopy activeCopy = copy(activeBook);
            checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                    atLimit.profile().getReaderNo(), activeCopy.getBarcode());
        }
        assertThrows(IllegalArgumentException.class,
                () -> reservations.createForReader(atLimit.account().getId(), target.getId()));

        ReaderFixture disabled = reader();
        accounts.disableReader(UserRole.ADMIN, disabled.account().getId());
        assertThrows(IllegalArgumentException.class,
                () -> reservations.createForReader(disabled.account().getId(), target.getId()));
    }

    @Test
    void concurrentDuplicateRequestsCreateOnlyOneActiveReservation() throws Exception {
        ReaderFixture requester = reader();
        ReaderFixture borrower = reader();
        Book book = book("并发预约书目 " + token());
        BookCopy copy = copy(book);
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), copy.getBarcode());

        List<Boolean> outcomes = race(
                () -> reservations.createForReader(requester.account().getId(), book.getId()),
                () -> reservations.createForReader(requester.account().getId(), book.getId()));

        assertEquals(1, outcomes.stream().filter(Boolean::booleanValue).count());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM reservation " +
                "WHERE reader_id = ? AND book_id = ? AND status = 'PENDING'", Long.class,
                requester.profile().getId(), book.getId()));
    }

    @Test
    void readerPageRequiresCsrfAndOnlyTheOwnerCanCancel() throws Exception {
        ReaderFixture requester = reader();
        ReaderFixture otherReader = reader();
        ReaderFixture borrower = reader();
        Book book = book("网页预约书目 " + token());
        BookCopy copy = copy(book);
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), copy.getBarcode());

        MockHttpSession requesterSession = login(requester.account());
        mvc.perform(get("/reader/reservations").session(requesterSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString("预约队列")));
        mvc.perform(post("/reader/reservations").session(requesterSession).param("bookId", book.getId().toString()))
                .andExpect(status().isForbidden());
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM reservation WHERE reader_id = ?",
                Long.class, requester.profile().getId()));

        mvc.perform(post("/reader/reservations").with(csrf()).session(requesterSession)
                        .param("bookId", book.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/reader/reservations"))
                .andExpect(flash().attribute("notice", containsString("已加入预约队列")));
        Reservation reservation = reservationRepository.findByBookIdAndStatusOrderByCreatedAtAscIdAsc(
                book.getId(), ReservationStatus.PENDING).get(0);

        MockHttpSession otherSession = login(otherReader.account());
        mvc.perform(post("/reader/reservations/{id}/cancel", reservation.getId()).with(csrf())
                        .session(otherSession))
                .andExpect(status().isForbidden());
        assertEquals(ReservationStatus.PENDING,
                reservationRepository.findById(reservation.getId()).orElseThrow().getStatus());

        mvc.perform(post("/reader/reservations/{id}/cancel", reservation.getId()).with(csrf())
                        .session(requesterSession))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/reader/reservations"))
                .andExpect(flash().attribute("notice", "预约已取消。"));
        Reservation cancelled = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.CANCELLED, cancelled.getStatus());
        assertNotNull(cancelled.getCancelledAt());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE actor_user_id = ? " +
                "AND target_type = 'RESERVATION' AND target_id = ? " +
                "AND action IN ('RESERVATION_CREATED', 'RESERVATION_CANCELLED')", Integer.class,
                requester.account().getId(), reservation.getId()));

        mvc.perform(post("/reader/reservations").with(csrf()).session(requesterSession)
                        .param("bookId", book.getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM reservation WHERE reader_id = ? " +
                "AND book_id = ? AND status = 'PENDING'", Long.class,
                requester.profile().getId(), book.getId()));

        mvc.perform(get("/reader/reservations").session(otherSession))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString(book.getTitle()))));
    }

    @Test
    void auditFailureRollsBackReservationCreation() throws Exception {
        ReaderFixture requester = reader();
        ReaderFixture borrower = reader();
        Book book = book("预约审计回滚 " + token());
        BookCopy copy = copy(book);
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), copy.getBarcode());
        MockHttpSession session = login(requester.account());
        doThrow(new DataIntegrityViolationException("模拟预约审计失败"))
                .when(auditLogs).recordCurrent(eq(requester.account().getId()), eq(UserRole.READER),
                        eq("RESERVATION_CREATED"), eq("RESERVATION"), anyLong(), eq("提交图书预约"));

        mvc.perform(post("/reader/reservations").with(csrf()).session(session)
                        .param("bookId", book.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("状态刚刚发生变化")));

        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM reservation WHERE reader_id = ? " +
                "AND book_id = ?", Long.class, requester.profile().getId(), book.getId()));
    }

    @Test
    void bookDetailCreatesForCurrentReaderAndShowsOwnProgressWithoutDuplicateRows() throws Exception {
        ReaderFixture requester = reader();
        ReaderFixture other = reader();
        ReaderFixture borrower = reader();
        Book book = book("详情预约 " + token());
        checkout(book, borrower);
        Book unrelated = book("忽略伪造参数 " + token());
        MockHttpSession session = login(requester.account());
        String action = "/reader/reservations/books/" + book.getId();
        mvc.perform(get("/catalog/{id}", book.getId()).session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("action=\"" + action + "\"")));
        assertFalse(reservationRepository.existsByBookIdAndStatusIn(book.getId(), List.of(ReservationStatus.PENDING)));

        var submitted = mvc.perform(post(action).with(csrf()).session(session)
                        .param("bookId", unrelated.getId().toString())
                        .param("readerUserId", other.account().getId().toString())
                        .param("returnUrl", "https://example.invalid/"))
                .andExpect(redirectedUrl("/catalog/" + book.getId() + "#reservation"))
                .andExpect(flash().attribute("notice", containsString("已加入预约队列"))).andReturn();
        Reservation row = reservationRepository.findByBookIdAndStatusOrderByCreatedAtAscIdAsc(book.getId(), ReservationStatus.PENDING).get(0);
        assertEquals(requester.profile().getId(), row.getReaderId());
        mvc.perform(get("/catalog/{id}", book.getId()).session(session).flashAttrs(submitted.getFlashMap()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("已加入预约队列")))
                .andExpect(content().string(containsString("你已加入这本书的预约队列")))
                .andExpect(content().string(not(containsString("action=\"" + action + "\""))));
        mvc.perform(post(action).with(csrf()).session(session))
                .andExpect(redirectedUrl("/catalog/" + book.getId() + "#reservation"))
                .andExpect(flash().attribute("error", containsString("有效预约")));
        assertEquals(1, reservationRepository.findByBookIdAndStatusOrderByCreatedAtAscIdAsc(book.getId(), ReservationStatus.PENDING).size());
        mvc.perform(get("/catalog/{id}", book.getId()).session(login(other.account())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("action=\"" + action + "\"")))
                .andExpect(content().string(not(containsString("你已加入这本书的预约队列"))));
        reservations.cancelForReader(requester.account().getId(), row.getId());
        mvc.perform(get("/catalog/{id}", book.getId()).session(session))
                .andExpect(status().isOk()).andExpect(content().string(containsString("action=\"" + action + "\"")));
        mvc.perform(get("/reader/reservations").session(session))
                .andExpect(content().string(containsString("从图书详情发起预约")))
                .andExpect(content().string(not(containsString("name=\"bookId\""))));
    }

    @Test
    void detailExplainsUnavailableChoicesAndSubmissionRechecksChangedInventory() throws Exception {
        ReaderFixture requester = reader();
        ReaderFixture borrower = reader();
        Book book = book("馆藏变化 " + token());
        Loan loan = checkout(book, borrower);
        MockHttpSession session = login(requester.account());
        String action = "/reader/reservations/books/" + book.getId();
        mvc.perform(get("/catalog/{id}", book.getId()).session(session))
                .andExpect(content().string(containsString("action=\"" + action + "\"")));
        mvc.perform(get("/catalog/{id}", book.getId()).session(login(borrower.account())))
                .andExpect(content().string(containsString("已有该书目的未还借阅")))
                .andExpect(content().string(not(containsString("action=\"" + action + "\""))));
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, loan.getId());
        mvc.perform(post(action).with(csrf()).session(session))
                .andExpect(redirectedUrl("/catalog/" + book.getId() + "#reservation"))
                .andExpect(flash().attribute("error", containsString("当前有可借副本")));
        mvc.perform(get("/catalog/{id}", book.getId()).session(session))
                .andExpect(content().string(containsString("请到馆办理借阅，无需预约")))
                .andExpect(content().string(not(containsString("action=\"" + action + "\""))));
        Book noCopies = book("尚未入库 " + token());
        mvc.perform(get("/catalog/{id}", noCopies.getId()).session(session))
                .andExpect(content().string(containsString("暂无可流通馆藏，暂不能预约")))
                .andExpect(content().string(not(containsString("action=\"/reader/reservations/books/"))));
        assertFalse(reservationRepository.existsByBookIdAndStatusIn(book.getId(), List.of(ReservationStatus.PENDING)));
    }

    @Test
    void bookReservationRouteRequiresReaderAndCsrfAndGetCannotCreate() throws Exception {
        ReaderFixture requester = reader();
        Book book = book("详情权限 " + token());
        checkout(book, reader());
        String action = "/reader/reservations/books/" + book.getId();
        MockHttpSession session = login(requester.account());
        MockHttpSession staffSession = login(librarian);
        mvc.perform(post(action).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(post(action).session(session)).andExpect(status().isForbidden());
        mvc.perform(post(action).with(csrf()).session(staffSession)).andExpect(status().isForbidden());
        mvc.perform(get(action).session(session)).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/catalog/{id}", book.getId()).session(staffSession))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("id=\"reservation\""))));
        assertFalse(reservationRepository.existsByBookIdAndStatusIn(book.getId(), List.of(ReservationStatus.PENDING)));
    }

    @Test
    void readyReservationIsShownAsHeldForCurrentReader() throws Exception {
        ReaderFixture requester = reader();
        Book book = book("详情到书保留 " + token());
        Loan loan = checkout(book, reader());
        Reservation created = reservations.createForReader(requester.account().getId(), book.getId());
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, loan.getId());
        assertEquals(ReservationStatus.READY, reservationRepository.findById(created.getId()).orElseThrow().getStatus());
        mvc.perform(get("/catalog/{id}", book.getId()).session(login(requester.account())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("这本书已为你保留")))
                .andExpect(content().string(not(containsString("action=\"/reader/reservations/books/"))));
    }

    private List<Boolean> race(ReservationCall first, ReservationCall second) throws Exception {
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

    private static boolean invokeRace(ReservationCall call, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发测试启动超时");
        try {
            call.run();
            return true;
        } catch (IllegalArgumentException expectedCompetition) {
            return false;
        }
    }

    private Loan checkout(Book book, ReaderFixture borrower) {
        BookCopy copy = copy(book);
        return checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), copy.getBarcode());
    }

    private ReaderFixture reader() {
        String suffix = token();
        AppUser user = accounts.createReader(UserRole.ADMIN, "reserve_reader_" + suffix, PASSWORD,
                "预约测试读者", "RESERVE-" + suffix, null);
        user.markPasswordChanged();
        AppUser saved = users.saveAndFlush(user);
        userIds.add(saved.getId());
        ReaderProfile profile = readers.findByUserId(saved.getId()).orElseThrow();
        profileIds.add(profile.getId());
        return new ReaderFixture(saved, profile);
    }

    private AppUser staff() {
        AppUser user = accounts.createUser("reserve_staff_" + token(), PASSWORD, "预约测试馆员",
                UserRole.LIBRARIAN);
        user.markPasswordChanged();
        AppUser saved = users.saveAndFlush(user);
        userIds.add(saved.getId());
        return saved;
    }

    private Book book(String title) {
        Book book = bookService.create(UserRole.ADMIN, title, "预约测试作者", null, null,
                null, category.getId(), null);
        bookIds.add(book.getId());
        return book;
    }

    private BookCopy copy(Book book) {
        BookCopy copy = copyService.register(UserRole.ADMIN, book.getId(), "RESERVE-" + token(), null);
        copyIds.add(copy.getId());
        return copy;
    }

    private MockHttpSession login(AppUser user) throws Exception {
        var result = mvc.perform(post("/login").with(csrf())
                        .param("username", user.getLoginName().toUpperCase(Locale.ROOT))
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private static String token() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @FunctionalInterface
    private interface ReservationCall {
        void run();
    }

    private record ReaderFixture(AppUser account, ReaderProfile profile) {
    }
}
