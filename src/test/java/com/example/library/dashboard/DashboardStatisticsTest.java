package com.example.library.dashboard;

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
import com.example.library.loan.Loan;
import com.example.library.loan.LoanCheckoutService;
import com.example.library.loan.LoanRepository;
import com.example.library.loan.LoanReturnService;
import com.example.library.loan.LoanRules;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardStatisticsTest {

    private static final String PASSWORD = "dashboard-test-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private ReaderProfileRepository readers;
    @Autowired private BookService bookService;
    @Autowired private BookRepository books;
    @Autowired private BookCopyService copyService;
    @Autowired private BookCopyRepository copies;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categories;
    @Autowired private LoanCheckoutService checkout;
    @Autowired private LoanReturnService returns;
    @Autowired private LoanRepository loans;
    @Autowired private LoanRules loanRules;
    @Autowired private JdbcTemplate jdbc;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> readerProfileIds = new ArrayList<>();
    private final List<Long> bookIds = new ArrayList<>();
    private final List<Long> copyIds = new ArrayList<>();
    private final List<Long> loanIds = new ArrayList<>();
    private Long categoryId;
    private AppUser admin;
    private AppUser librarian;
    private ReaderProfile overdueReader;
    private ReaderProfile lateReturnReader;
    private ReaderProfile emptyReader;

    @BeforeEach
    void prepare() {
        Category category = categoryService.create(UserRole.ADMIN, "统计分类-" + token());
        categoryId = category.getId();
        admin = staff(UserRole.ADMIN);
        librarian = staff(UserRole.LIBRARIAN);
        overdueReader = reader("逾期读者");
        lateReturnReader = reader("晚还读者");
        emptyReader = reader("无借阅读者");
    }

    @AfterEach
    void clean() {
        readerProfileIds.forEach(id -> jdbc.update("DELETE FROM loan WHERE reader_id = ?", id));
        copyIds.forEach(id -> copies.findById(id).ifPresent(copies::delete));
        copies.flush();
        bookIds.forEach(id -> books.findById(id).ifPresent(books::delete));
        books.flush();
        if (categoryId != null) categories.findById(categoryId).ifPresent(categories::delete);
        categories.flush();
        readerProfileIds.forEach(id -> readers.findById(id).ifPresent(readers::delete));
        readers.flush();
        userIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        userIds.clear();
        readerProfileIds.clear();
        bookIds.clear();
        copyIds.clear();
        loanIds.clear();
    }

    @Test
    void dashboardsCountBusinessFactsRespectRolesAndStayReadOnly() throws Exception {
        StaffDashboardStats baseline = currentStaffCounts();
        Book activeBook = book("统计正常书目");
        Book archivedBook = book("统计归档书目");

        BookCopy availableCopy = copy(activeBook, "统计可借");
        BookCopy overdueCopy = copy(activeBook, "统计逾期");
        BookCopy lateReturnedCopy = copy(activeBook, "统计晚还");
        BookCopy withdrawnActiveCopy = copy(activeBook, "统计注销");
        BookCopy withdrawnArchivedCopy = copy(archivedBook, "归档后注销");

        Loan overdueLoan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                overdueReader.getReaderNo(), overdueCopy.getBarcode());
        setDueDate(overdueLoan, loanRules.today().minusDays(1));
        Loan lateReturnLoan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                lateReturnReader.getReaderNo(), lateReturnedCopy.getBarcode());
        setDueDate(lateReturnLoan, loanRules.today().minusDays(1));
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, lateReturnLoan.getId());

        copyService.withdraw(UserRole.ADMIN, withdrawnActiveCopy.getId());
        copyService.withdraw(UserRole.ADMIN, withdrawnArchivedCopy.getId());
        bookService.archive(UserRole.ADMIN, archivedBook.getId());

        StaffDashboardStats fixedFixtures = staffStats(baseline,
                2, 5, 3, 2, 1, 1);
        Map<Long, String> copyStateBeforeRead = copyStateSnapshot();
        Map<Long, String> loanStateBeforeRead = loanStateSnapshot();
        long auditCountBeforeRead = auditCount();

        MockHttpSession adminSession = login(admin);
        MockHttpSession librarianSession = login(librarian);
        MockHttpSession overdueSession = login(overdueReader.getUserId());
        MockHttpSession lateReturnSession = login(lateReturnReader.getUserId());
        MockHttpSession emptySession = login(emptyReader.getUserId());

        assertEquals(fixedFixtures, staffStatsFromPage("/staff", librarianSession));
        assertEquals(fixedFixtures, staffStatsFromPage("/admin", adminSession));

        ReaderDashboardStats overdueStats = readerStatsFromPage(overdueSession);
        assertEquals(new ReaderDashboardStats(1, 1), overdueStats);
        assertEquals(new ReaderDashboardStats(0, 0), readerStatsFromPage(lateReturnSession));
        assertEquals(new ReaderDashboardStats(0, 0), readerStatsFromPage(emptySession));

        mvc.perform(get("/staff").session(overdueSession)).andExpect(status().isForbidden());
        mvc.perform(get("/admin").session(librarianSession)).andExpect(status().isForbidden());
        String readerPage = mvc.perform(get("/reader").session(overdueSession))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(readerPage.contains("我的未还借阅"));
        assertFalse(readerPage.contains("书目总数"));

        assertEquals(copyStateBeforeRead, copyStateSnapshot());
        assertEquals(loanStateBeforeRead, loanStateSnapshot());
        assertEquals(auditCountBeforeRead, auditCount());

        Book operationBook = book("借还后更新统计");
        BookCopy operationCopy = copy(operationBook, "借还更新");
        Loan operationLoan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                emptyReader.getReaderNo(), operationCopy.getBarcode());
        loanIds.add(operationLoan.getId());
        setDueDate(operationLoan, loanRules.today().minusDays(1));
        assertEquals(staffStats(fixedFixtures, 1, 1, 1, 0, 1, 1),
                staffStatsFromPage("/staff", librarianSession));

        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, operationLoan.getId());
        assertEquals(staffStats(fixedFixtures, 1, 1, 1, 1, 0, 0),
                staffStatsFromPage("/staff", librarianSession));
        assertEquals(BookCopyStatus.AVAILABLE, copies.findById(operationCopy.getId()).orElseThrow().getStatus());
    }

    private StaffDashboardStats currentStaffCounts() {
        LocalDate today = loanRules.today();
        return new StaffDashboardStats(books.count(), copies.count(),
                copies.countByStatusNot(BookCopyStatus.WITHDRAWN),
                copies.countByStatus(BookCopyStatus.AVAILABLE),
                loans.countByReturnedAtIsNull(), loans.countByReturnedAtIsNullAndDueDateBefore(today));
    }

    private static StaffDashboardStats staffStats(StaffDashboardStats base,
                                                 long books, long total, long valid, long available,
                                                 long current, long overdue) {
        return new StaffDashboardStats(base.bookTitles() + books, base.copiesTotal() + total,
                base.copiesValid() + valid, base.copiesAvailable() + available,
                base.currentLoans() + current, base.currentOverdueLoans() + overdue);
    }

    private StaffDashboardStats staffStatsFromPage(String path, MockHttpSession session) throws Exception {
        var result = mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn();
        assertNotNull(result.getModelAndView());
        String page = result.getResponse().getContentAsString();
        assertTrue(page.contains("全馆概况"));
        assertTrue(page.contains("当前逾期未还"));
        return (StaffDashboardStats) result.getModelAndView().getModel().get("staffStats");
    }

    private ReaderDashboardStats readerStatsFromPage(MockHttpSession session) throws Exception {
        var result = mvc.perform(get("/reader").session(session)).andExpect(status().isOk()).andReturn();
        assertNotNull(result.getModelAndView());
        assertTrue(result.getResponse().getContentAsString().contains("我的未还借阅"));
        return (ReaderDashboardStats) result.getModelAndView().getModel().get("readerStats");
    }

    private Map<Long, String> copyStateSnapshot() {
        Map<Long, String> states = new LinkedHashMap<>();
        for (Long id : copyIds) {
            states.put(id, jdbc.queryForObject(
                    "SELECT CONCAT(status, ':', version) FROM book_copy WHERE id = ?", String.class, id));
        }
        return states;
    }

    private Map<Long, String> loanStateSnapshot() {
        Map<Long, String> states = new LinkedHashMap<>();
        for (Long id : loanIds) {
            states.put(id, jdbc.queryForObject(
                    "SELECT CONCAT(COALESCE(DATE_FORMAT(returned_at, '%Y-%m-%d %H:%i:%s.%f'), 'OPEN'), ':', version) " +
                            "FROM loan WHERE id = ?", String.class, id));
        }
        return states;
    }

    private long auditCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Long.class);
    }

    private void setDueDate(Loan loan, LocalDate dueDate) {
        jdbc.update("UPDATE loan SET due_date = ? WHERE id = ?", dueDate, loan.getId());
        loanIds.add(loan.getId());
    }

    private AppUser staff(UserRole role) {
        AppUser user = accounts.createUser("dashboard_" + role.name().toLowerCase(Locale.ROOT) + "_" + token(),
                PASSWORD, "统计测试员工", role);
        user.markPasswordChanged();
        userIds.add(users.saveAndFlush(user).getId());
        return user;
    }

    private ReaderProfile reader(String label) {
        String suffix = token();
        AppUser user = accounts.createReader(UserRole.ADMIN, "dashboard_reader_" + suffix, PASSWORD,
                label + suffix.substring(0, 4), "DASH-" + suffix, null);
        user.markPasswordChanged();
        users.saveAndFlush(user);
        userIds.add(user.getId());
        ReaderProfile profile = readers.findByUserId(user.getId()).orElseThrow();
        readerProfileIds.add(profile.getId());
        return profile;
    }

    private Book book(String title) {
        Book book = bookService.create(UserRole.ADMIN, title + "-" + token(), "统计测试作者",
                null, null, null, categoryId, null);
        bookIds.add(book.getId());
        return book;
    }

    private BookCopy copy(Book book, String label) {
        BookCopy copy = copyService.register(UserRole.ADMIN, book.getId(),
                "DASH-" + label + "-" + token(), "统计测试书架");
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
}
