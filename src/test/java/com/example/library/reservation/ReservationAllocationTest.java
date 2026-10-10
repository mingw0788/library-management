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
import com.example.library.catalog.BookCopyStatus;
import com.example.library.catalog.BookRepository;
import com.example.library.catalog.BookService;
import com.example.library.catalog.CatalogService;
import com.example.library.catalog.Category;
import com.example.library.catalog.CategoryRepository;
import com.example.library.catalog.CategoryService;
import com.example.library.loan.Loan;
import com.example.library.loan.LoanCheckoutService;
import com.example.library.loan.LoanRenewalService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
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
class ReservationAllocationTest {

    private static final String PASSWORD = "reservation-hold-test-pass-123";

    @Autowired private ReservationService reservations;
    @Autowired private ReservationRepository reservationRows;
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
    @Autowired private CatalogService catalog;
    @Autowired private BookCopyRepository copies;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private Clock circulationClock;
    @Autowired private MockMvc mvc;
    @MockitoSpyBean private AuditLogService auditLogs;

    private final List<Long> profileIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> copyIds = new ArrayList<>();
    private final List<Long> bookIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private Category category;
    private AppUser librarian;
    private AppUser secondLibrarian;

    @BeforeEach
    void prepare() {
        reset(auditLogs);
        category = categoryService.create(UserRole.ADMIN, "X03 分类-" + token());
        categoryIds.add(category.getId());
        librarian = staff(UserRole.LIBRARIAN);
        secondLibrarian = staff(UserRole.LIBRARIAN);
    }

    @AfterEach
    void clean() {
        reset(auditLogs);
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
    }

    @Test
    void distributesReturnsAndNewCopiesInFifoOrderWithA72HourHold() throws Exception {
        ReaderFixture first = reader();
        ReaderFixture second = reader();
        ReaderFixture borrower = reader();
        Book book = book("先进先出");
        BookCopy firstCopy = copy(book);
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), firstCopy.getBarcode());
        Reservation firstReservation = reservations.createForReader(first.account().getId(), book.getId());
        Reservation secondReservation = reservations.createForReader(second.account().getId(), book.getId());

        Instant allocationWindowStart = circulationClock.instant();
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, loan.getId());
        Instant allocationWindowEnd = circulationClock.instant();

        Reservation readyFirst = reservationRows.findById(firstReservation.getId()).orElseThrow();
        Reservation stillWaiting = reservationRows.findById(secondReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.READY, readyFirst.getStatus());
        assertEquals(firstCopy.getId(), readyFirst.getCopyId());
        assertEquals(ReservationStatus.PENDING, stillWaiting.getStatus());
        assertEquals(BookCopyStatus.HELD, copies.findById(firstCopy.getId()).orElseThrow().getStatus());
        assertFalse(readyFirst.getHoldExpiresAt().isBefore(
                allocationWindowStart.plus(Duration.ofHours(72)).minusMillis(2)));
        assertFalse(readyFirst.getHoldExpiresAt().isAfter(
                allocationWindowEnd.plus(Duration.ofHours(72)).plusMillis(2)));
        assertThrows(IllegalArgumentException.class,
                () -> reservations.createForReader(first.account().getId(), book.getId()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO reservation(reader_id, book_id, status, created_at, version) " +
                        "VALUES (?, ?, 'PENDING', UTC_TIMESTAMP(6), 0)", first.profile().getId(), book.getId()));

        MockHttpSession readerSession = login(first.account());
        mvc.perform(get("/reader/reservations").session(readerSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString("已到书，等待取书")));
        mvc.perform(get("/catalog/{id}", book.getId()).session(readerSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString("预约保留")));
        MockHttpSession staffSession = login(librarian);
        mvc.perform(get("/staff/reservations").session(staffSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString("核对读者证号")));

        BookCopy newlyRegistered = copy(book);
        Reservation readySecond = reservationRows.findById(secondReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.READY, readySecond.getStatus());
        assertEquals(newlyRegistered.getId(), readySecond.getCopyId());
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_copy WHERE book_id = ? AND status = 'HELD'", Long.class, book.getId()));
        assertEquals(2L, copyService.inventoryByBookIds(List.of(book.getId())).get(book.getId()).held());
        assertTrue(catalog.detail(book.getId()).copies().stream()
                .allMatch(row -> row.status() == BookCopyStatus.HELD));
        assertEquals(2L, reservations.listReadyForStaff(UserRole.LIBRARIAN).stream()
                .filter(row -> row.bookTitle().equals(book.getTitle())).count());
    }

    @Test
    void pickupChecksTheReservedReaderAndKeepsAnIneligibleHoldUntilItsDeadline() {
        ReaderFixture owner = reader();
        ReaderFixture borrower = reader();
        Book targetBook = book("资格复核");
        BookCopy targetCopy = copy(targetBook);

        for (int index = 0; index < 4; index++) {
            Book anotherBook = book("已借书目 " + index);
            checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                    owner.profile().getReaderNo(), copy(anotherBook).getBarcode());
        }
        Loan targetLoan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), targetCopy.getBarcode());
        Reservation reservation = reservations.createForReader(owner.account().getId(), targetBook.getId());
        Book fifthBook = book("额度内最后一册");
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                owner.profile().getReaderNo(), copy(fifthBook).getBarcode());
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, targetLoan.getId());

        assertThrows(IllegalArgumentException.class, () -> reservations.fulfillForReader(
                librarian.getId(), UserRole.LIBRARIAN, reservation.getId(), borrower.profile().getReaderNo()));
        assertThrows(IllegalArgumentException.class, () -> reservations.fulfillForReader(
                librarian.getId(), UserRole.LIBRARIAN, reservation.getId(), owner.profile().getReaderNo()));

        Reservation stillReady = reservationRows.findById(reservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.READY, stillReady.getStatus());
        assertEquals(BookCopyStatus.HELD, copies.findById(targetCopy.getId()).orElseThrow().getStatus());
        assertEquals(5L, loans.countByReader_IdAndReturnedAtIsNull(owner.profile().getId()));
    }

    @Test
    void successfulPickupIsAtomicAndAnAuditFailureRollsBackAllThreeRecords() throws Exception {
        ReaderFixture owner = reader();
        ReaderFixture borrower = reader();
        Book book = book("预约取书");
        BookCopy copy = copy(book);
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), copy.getBarcode());
        Reservation reservation = reservations.createForReader(owner.account().getId(), book.getId());
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, loan.getId());

        doThrow(new IllegalStateException("simulated audit failure")).when(auditLogs)
                .recordCurrent(eq(librarian.getId()), eq(UserRole.LIBRARIAN), eq("RESERVATION_FULFILLED"),
                        eq("RESERVATION"), anyLong(), anyString());
        assertThrows(IllegalStateException.class, () -> reservations.fulfillForReader(
                librarian.getId(), UserRole.LIBRARIAN, reservation.getId(), owner.profile().getReaderNo()));
        assertEquals(ReservationStatus.READY, reservationRows.findById(reservation.getId()).orElseThrow().getStatus());
        assertEquals(BookCopyStatus.HELD, copies.findById(copy.getId()).orElseThrow().getStatus());
        assertEquals(0L, loans.countByReader_IdAndReturnedAtIsNull(owner.profile().getId()));

        reset(auditLogs);
        MockHttpSession staffSession = login(librarian);
        mvc.perform(post("/staff/reservations/{id}/fulfill", reservation.getId()).with(csrf())
                        .session(staffSession).param("readerNo", owner.profile().getReaderNo()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/staff/reservations"))
                .andExpect(flash().attribute("notice", containsString("已为读者办理取书")));
        assertEquals(ReservationStatus.FULFILLED, reservationRows.findById(reservation.getId()).orElseThrow().getStatus());
        assertEquals(BookCopyStatus.ON_LOAN, copies.findById(copy.getId()).orElseThrow().getStatus());
        assertEquals(owner.profile().getId(), jdbc.queryForObject(
                "SELECT reader_id FROM loan WHERE copy_id = ? AND returned_at IS NULL", Long.class, copy.getId()));
    }

    @Test
    void cancellationAndDisableReleaseAReadyCopyToTheNextReader() {
        ReaderFixture first = reader();
        ReaderFixture second = reader();
        ReaderFixture third = reader();
        ReaderFixture borrower = reader();
        Book book = book("取消和停用释放");
        BookCopy copy = copy(book);
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), copy.getBarcode());
        Reservation firstReservation = reservations.createForReader(first.account().getId(), book.getId());
        Reservation secondReservation = reservations.createForReader(second.account().getId(), book.getId());
        Reservation thirdReservation = reservations.createForReader(third.account().getId(), book.getId());
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, loan.getId());

        reservations.cancelForReader(first.account().getId(), firstReservation.getId());
        assertEquals(ReservationStatus.CANCELLED, reservationRows.findById(firstReservation.getId()).orElseThrow().getStatus());
        assertEquals(ReservationStatus.READY, reservationRows.findById(secondReservation.getId()).orElseThrow().getStatus());
        assertEquals(copy.getId(), reservationRows.findById(secondReservation.getId()).orElseThrow().getCopyId());

        accounts.disableReader(UserRole.ADMIN, second.account().getId());
        assertEquals(ReservationStatus.CANCELLED, reservationRows.findById(secondReservation.getId()).orElseThrow().getStatus());
        Reservation thirdReady = reservationRows.findById(thirdReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.READY, thirdReady.getStatus());
        assertEquals(copy.getId(), thirdReady.getCopyId());
        assertEquals(BookCopyStatus.HELD, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void competingReturnAndCancellationStillLeaveTheNextReaderWithTheCopy() throws Exception {
        ReaderFixture first = reader();
        ReaderFixture second = reader();
        ReaderFixture borrower = reader();
        Book book = book("并发取消与归还");
        BookCopy copy = copy(book);
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), copy.getBarcode());
        Reservation firstReservation = reservations.createForReader(first.account().getId(), book.getId());
        Reservation secondReservation = reservations.createForReader(second.account().getId(), book.getId());

        race(
                () -> returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, loan.getId()),
                () -> reservations.cancelForReader(first.account().getId(), firstReservation.getId()));

        assertEquals(ReservationStatus.CANCELLED, reservationRows.findById(firstReservation.getId()).orElseThrow().getStatus());
        Reservation next = reservationRows.findById(secondReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.READY, next.getStatus());
        assertEquals(copy.getId(), next.getCopyId());
        assertEquals(BookCopyStatus.HELD, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void competingReturnAndDisableStillReleaseTheCopyToTheNextReader() throws Exception {
        ReaderFixture first = reader();
        ReaderFixture second = reader();
        ReaderFixture borrower = reader();
        Book book = book("并发停用与归还");
        BookCopy copy = copy(book);
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), copy.getBarcode());
        Reservation firstReservation = reservations.createForReader(first.account().getId(), book.getId());
        Reservation secondReservation = reservations.createForReader(second.account().getId(), book.getId());

        race(
                () -> returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, loan.getId()),
                () -> accounts.disableReader(UserRole.ADMIN, first.account().getId()));

        assertEquals(ReservationStatus.CANCELLED, reservationRows.findById(firstReservation.getId()).orElseThrow().getStatus());
        Reservation next = reservationRows.findById(secondReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.READY, next.getStatus());
        assertEquals(copy.getId(), next.getCopyId());
        assertEquals(BookCopyStatus.HELD, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void simultaneousReturnsAssignDifferentCopiesToTheNextReaders() throws Exception {
        ReaderFixture firstBorrower = reader();
        ReaderFixture secondBorrower = reader();
        ReaderFixture firstQueued = reader();
        ReaderFixture secondQueued = reader();
        Book book = book("多副本同时归还");
        BookCopy firstCopy = copy(book);
        BookCopy secondCopy = copy(book);
        Loan firstLoan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                firstBorrower.profile().getReaderNo(), firstCopy.getBarcode());
        Loan secondLoan = checkout.checkout(secondLibrarian.getId(), UserRole.LIBRARIAN,
                secondBorrower.profile().getReaderNo(), secondCopy.getBarcode());
        Reservation firstReservation = reservations.createForReader(firstQueued.account().getId(), book.getId());
        Reservation secondReservation = reservations.createForReader(secondQueued.account().getId(), book.getId());

        race(
                () -> returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, firstLoan.getId()),
                () -> returns.returnByLoanId(secondLibrarian.getId(), UserRole.LIBRARIAN, secondLoan.getId()));

        Reservation firstReady = reservationRows.findById(firstReservation.getId()).orElseThrow();
        Reservation secondReady = reservationRows.findById(secondReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.READY, firstReady.getStatus());
        assertEquals(ReservationStatus.READY, secondReady.getStatus());
        assertNotEquals(firstReady.getCopyId(), secondReady.getCopyId());
        assertEquals(2L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM book_copy WHERE book_id = ? AND status = 'HELD'", Long.class, book.getId()));
    }

    @Test
    void twoStaffRequestsCannotFulfillTheSameReservationTwice() throws Exception {
        ReaderFixture owner = reader();
        ReaderFixture borrower = reader();
        Book book = book("并发取书");
        BookCopy copy = copy(book);
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), copy.getBarcode());
        Reservation reservation = reservations.createForReader(owner.account().getId(), book.getId());
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, loan.getId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> request = () -> {
                ready.countDown();
                start.await();
                try {
                    reservations.fulfillForReader(librarian.getId(), UserRole.LIBRARIAN,
                            reservation.getId(), owner.profile().getReaderNo());
                    return true;
                } catch (IllegalArgumentException expectedAlreadyFulfilled) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(request);
            Future<Boolean> second = executor.submit(request);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int successes = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successes);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(ReservationStatus.FULFILLED, reservationRows.findById(reservation.getId()).orElseThrow().getStatus());
        assertEquals(1L, loans.countByReader_IdAndReturnedAtIsNull(owner.profile().getId()));
        assertEquals(BookCopyStatus.ON_LOAN, copies.findById(copy.getId()).orElseThrow().getStatus());
    }

    @Test
    void anActiveReservationBlocksRenewalAndArchive() {
        ReaderFixture borrower = reader();
        ReaderFixture queued = reader();
        Book book = book("预约保护书目");
        BookCopy copy = copy(book);
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), copy.getBarcode());
        reservations.createForReader(queued.account().getId(), book.getId());

        assertThrows(IllegalArgumentException.class, () -> renewals.renewForReader(
                borrower.account().getId(), loan.getId()));
        IllegalArgumentException archiveFailure = assertThrows(IllegalArgumentException.class,
                () -> bookService.archive(UserRole.ADMIN, book.getId()));
        assertTrue(archiveFailure.getMessage().contains("活动预约"));
    }

    @Test
    void activeReservationBlocksWithdrawalOfTheLastAvailableCopy() {
        ReaderFixture borrower = reader();
        ReaderFixture queued = reader();
        Book book = book("最后一册保护");
        BookCopy copy = copy(book);
        Loan loan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                borrower.profile().getReaderNo(), copy.getBarcode());
        reservations.createForReader(queued.account().getId(), book.getId());
        // Model a stale external inventory edit so this guard is tested independently of normal allocation.
        jdbc.update("UPDATE book_copy SET status = 'AVAILABLE' WHERE id = ?", copy.getId());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> copyService.withdraw(UserRole.LIBRARIAN, copy.getId()));
        assertTrue(failure.getMessage().contains("最后一册可流通副本"));
        assertEquals(BookCopyStatus.AVAILABLE, copies.findById(copy.getId()).orElseThrow().getStatus());
        assertEquals(0, loan.getRenewCount());
    }

    private void race(Runnable first, Runnable second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> firstCall = () -> { ready.countDown(); start.await(); first.run(); return null; };
            Callable<Void> secondCall = () -> { ready.countDown(); start.await(); second.run(); return null; };
            Future<Void> firstResult = executor.submit(firstCall);
            Future<Void> secondResult = executor.submit(secondCall);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            firstResult.get(20, TimeUnit.SECONDS);
            secondResult.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private ReaderFixture reader() {
        String suffix = token();
        AppUser user = accounts.createReader(UserRole.ADMIN, "x03_reader_" + suffix, PASSWORD,
                "X03 测试读者", "X03-" + suffix, null);
        user.markPasswordChanged();
        AppUser saved = users.saveAndFlush(user);
        userIds.add(saved.getId());
        ReaderProfile profile = readers.findByUserId(saved.getId()).orElseThrow();
        profileIds.add(profile.getId());
        return new ReaderFixture(saved, profile);
    }

    private AppUser staff(UserRole role) {
        AppUser user = accounts.createUser("x03_staff_" + token(), PASSWORD, "X03 测试工作人员", role);
        user.markPasswordChanged();
        AppUser saved = users.saveAndFlush(user);
        userIds.add(saved.getId());
        return saved;
    }

    private Book book(String label) {
        Book book = bookService.create(UserRole.ADMIN, label + " " + token(), "X03 测试作者", null,
                null, null, category.getId(), null);
        bookIds.add(book.getId());
        return book;
    }

    private BookCopy copy(Book book) {
        BookCopy copy = copyService.register(UserRole.ADMIN, book.getId(), "X03-" + token(), null);
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

    private record ReaderFixture(AppUser account, ReaderProfile profile) { }
}
