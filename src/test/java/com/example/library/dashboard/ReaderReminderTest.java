package com.example.library.dashboard;

import com.example.library.account.*;
import com.example.library.catalog.*;
import com.example.library.loan.*;
import com.example.library.reservation.*;
import com.example.library.security.AppUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Date;
import java.time.*;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "library.circulation.maximum-active-loans=10")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReaderReminderTest.ClockConfiguration.class)
@Transactional
class ReaderReminderTest {
    private static final Instant NOW = Instant.parse("2026-10-09T16:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ReaderReminderService reminders;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private ReaderProfileRepository readers;
    @Autowired private CategoryService categories;
    @Autowired private BookService books;
    @Autowired private BookCopyService copies;
    @Autowired private BookCopyRepository copyRows;
    @Autowired private LoanCheckoutService checkout;
    @Autowired private LoanReturnService returns;
    @Autowired private LoanRepository loans;
    @Autowired private ReservationRepository reservations;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReminderClock clock;
    private AppUser reader;
    private AppUser staff;
    private Category category;

    @BeforeEach
    void prepare() {
        clock.now = NOW;
        category = categories.create(UserRole.ADMIN, "提醒分类" + token());
        reader = reader();
        staff = accounts.createUser("reminder_staff_" + token(), "reminder-test-pass-123", "提醒馆员", UserRole.LIBRARIAN);
        staff.markPasswordChanged();
        users.saveAndFlush(staff);
    }

    @Test
    void businessDateBoundariesReturnedRecordsAndOwnershipAreCorrect() throws Exception {
        List<Loan> mine = new ArrayList<>();
        for (int index = 0; index < 5; index++) mine.add(loan(reader));
        returns.returnByLoanId(staff.getId(), UserRole.LIBRARIAN, mine.getLast().getId());
        LocalDate today = LocalDate.of(2026, 10, 10); // UTC is still October 9.
        due(mine.get(0), today.minusDays(1));
        due(mine.get(1), today);
        due(mine.get(2), today.plusDays(3));
        due(mine.get(3), today.plusDays(4));
        due(mine.get(4), today.minusDays(2));
        AppUser other = reader();
        Loan othersLoan = loan(other);
        due(othersLoan, today.minusDays(10));
        ReaderReminders result = reminders.forReader(reader.getId());
        assertEquals(today, result.today());
        assertEquals("Asia/Shanghai", result.timeZone());
        assertEquals(1, result.overdueCount());
        assertEquals(2, result.dueSoonCount());
        assertEquals(mine.getFirst().getId(), result.overdue().getFirst().loanId());
        assertEquals(List.of(mine.get(1).getId(), mine.get(2).getId()), result.dueSoon().stream().map(LoanReminderItem::loanId).toList());
        assertEquals("今天应还", result.dueSoon().getFirst().dueLabel(today));
        assertEquals("还有 3 天应还", result.dueSoon().getLast().dueLabel(today));
        assertEquals("已逾期 1 天", result.overdue().getFirst().dueLabel(today));
        var response = mvc.perform(get("/").with(user(new AppUserPrincipal(reader)))
                        .param("readerUserId", other.getId().toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("今天应还")))
                .andExpect(content().string(containsString("已逾期 1 天"))).andReturn();
        ReaderReminders page = (ReaderReminders) response.getModelAndView().getModel().get("readerReminders");
        assertEquals(result.overdue(), page.overdue(), "客户端读者编号不能改变首页身份");
        assertEquals(0, page.readyCount());
        mvc.perform(get("/reader").with(user(new AppUserPrincipal(reader))))
                .andExpect(status().isOk()).andExpect(model().attribute("readerReminders", result))
                .andExpect(content().string(containsString("今天应还")));
    }

    @Test
    void readyDeadlineIsExclusiveAndExpiredReadyIsNotMutatedByHome() throws Exception {
        Reservation valid = ready(reader, NOW.plusNanos(1000));
        Reservation boundary = ready(reader, NOW);
        ready(reader(), NOW.plusSeconds(3600));
        Reservation cancelled = new Reservation(profile(reader).getId(), book().getId(), NOW.minusSeconds(60));
        cancelled.cancel(NOW);
        reservations.saveAndFlush(cancelled);
        Reservation pending = new Reservation(profile(reader).getId(), book().getId(), NOW.minusSeconds(60));
        reservations.saveAndFlush(pending);
        long count = reservations.count();
        ReaderReminders result = reminders.forReader(reader.getId());
        assertEquals(1, result.readyCount());
        assertEquals(valid.getId(), result.ready().getFirst().reservationId());
        assertEquals("2026-10-10 00:00:00", result.ready().getFirst().deadline(result.timeZone()));
        mvc.perform(get("/").with(user(new AppUserPrincipal(reader)))).andExpect(status().isOk())
                .andExpect(content().string(containsString("取书截止：")))
                .andExpect(content().string(containsString("2026-10-10 00:00:00")));
        clock.now = NOW.plusNanos(1000);
        assertEquals(0, reminders.forReader(reader.getId()).readyCount());
        assertEquals(count, reservations.count());
        assertEquals(ReservationStatus.READY, reservations.findById(boundary.getId()).orElseThrow().getStatus());
        assertEquals(ReservationStatus.READY, reservations.findById(valid.getId()).orElseThrow().getStatus());
    }

    @Test
    void previewsAreBoundedOrderedAndTotalsRemainComplete() throws Exception {
        List<Loan> borrowed = new ArrayList<>();
        for (int index = 0; index < 7; index++) borrowed.add(loan(reader));
        LocalDate today = LocalDate.of(2026, 10, 10);
        for (int index = 0; index < borrowed.size(); index++) due(borrowed.get(index), today.minusDays(index + 1));
        List<Reservation> held = new ArrayList<>();
        for (int index = 6; index >= 0; index--) held.add(ready(reader, NOW.plusSeconds(60 + index)));
        ReaderReminders result = reminders.forReader(reader.getId());
        assertEquals(7, result.overdueCount());
        assertEquals(5, result.overdue().size());
        assertEquals(borrowed.getLast().getId(), result.overdue().getFirst().loanId());
        assertEquals(7, result.readyCount());
        assertEquals(5, result.ready().size());
        assertEquals(held.getLast().getId(), result.ready().getFirst().reservationId());
        mvc.perform(get("/").with(user(new AppUserPrincipal(reader)))).andExpect(status().isOk())
                .andExpect(content().string(containsString("仅列出应还日期最早的 5 项")))
                .andExpect(content().string(containsString("仅列出截止时间最早的 5 项")));
    }

    @Test
    void emptyMissingProfileAnonymousAndStaffStatesAreClear() throws Exception {
        var empty = reminders.forReader(reader.getId());
        assertTrue(empty.profileAvailable());
        assertEquals(0, empty.overdueCount() + empty.dueSoonCount() + empty.readyCount());
        mvc.perform(get("/").with(user(new AppUserPrincipal(reader)))).andExpect(status().isOk())
                .andExpect(content().string(containsString("没有逾期未还的图书")))
                .andExpect(content().string(containsString("近期没有需要归还的图书")));
        var bare = accounts.createUser("reminder_bare_" + token(), "reminder-test-pass-123", "无档案读者", UserRole.READER);
        bare.markPasswordChanged();
        users.saveAndFlush(bare);
        mvc.perform(get("/").with(user(new AppUserPrincipal(bare)))).andExpect(status().isOk())
                .andExpect(content().string(containsString("请联系馆员完善资料")));
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("readerReminders"));
        mvc.perform(get("/").with(user(new AppUserPrincipal(staff)))).andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("readerReminders"))
                .andExpect(content().string(not(containsString("id=\"reminders-title\""))));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> reminders.forReader(staff.getId()));
    }

    private AppUser reader() {
        AppUser account = accounts.createReader(UserRole.ADMIN, "reminder_reader_" + token(), "reminder-test-pass-123",
                "提醒读者", "REMINDER-" + token(), null);
        account.markPasswordChanged();
        return users.saveAndFlush(account);
    }

    private ReaderProfile profile(AppUser account) { return readers.findByUserId(account.getId()).orElseThrow(); }

    private Book book() { return books.create(UserRole.ADMIN, "提醒图书" + token(), "作者", null, null, null, category.getId(), null); }

    private Loan loan(AppUser account) {
        BookCopy copy = copies.register(UserRole.ADMIN, book().getId(), "REMINDER-" + token(), "A区");
        return checkout.checkout(staff.getId(), UserRole.LIBRARIAN, profile(account).getReaderNo(), copy.getBarcode());
    }

    private void due(Loan loan, LocalDate date) {
        jdbc.update("UPDATE loan SET due_date = ? WHERE id = ?", Date.valueOf(date), loan.getId());
    }

    private Reservation ready(AppUser account, Instant deadline) {
        Book book = book();
        BookCopy copy = copies.register(UserRole.ADMIN, book.getId(), "REMINDER-" + token(), "A区");
        copy.holdForReservation();
        copyRows.saveAndFlush(copy);
        Reservation reservation = new Reservation(profile(account).getId(), book.getId(), NOW.minusSeconds(60));
        reservation.assignCopy(copy.getId(), deadline);
        return reservations.saveAndFlush(reservation);
    }

    private static String token() { return UUID.randomUUID().toString().replace("-", "").substring(0, 10); }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean @Primary ReminderClock reminderClock() { return new ReminderClock(); }
    }

    static class ReminderClock extends Clock {
        volatile Instant now = NOW;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
