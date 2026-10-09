package com.example.library.loan;

import com.example.library.account.AccountService;
import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.ReaderProfile;
import com.example.library.account.ReaderProfileRepository;
import com.example.library.account.UserRole;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoanQueryTest {

    private static final String PASSWORD = "loan-query-test-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private LoanCheckoutService checkout;
    @Autowired private LoanReturnService returns;
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
        category = categoryService.create(UserRole.ADMIN, "T15 分类-" + token());
        categoryIds.add(category.getId());
        librarian = staff();
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
        profileIds.clear(); userIds.clear(); copyIds.clear(); bookIds.clear(); categoryIds.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void readerCanOnlySeeOwnLoansEvenWhenRequestNamesAnotherReader() throws Exception {
        ReaderProfile otherReader = reader();
        String ownTitle = "本人借阅-" + token();
        String returnedTitle = "本人历史-" + token();
        String otherTitle = "他人借阅-" + token();
        BookCopy ownCopy = copy(ownTitle);
        BookCopy returnedCopy = copy(returnedTitle);
        BookCopy otherCopy = copy(otherTitle);
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN, reader.getReaderNo(), ownCopy.getBarcode());
        Loan returnedLoan = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader.getReaderNo(), returnedCopy.getBarcode());
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN, otherReader.getReaderNo(), otherCopy.getBarcode());
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, returnedLoan.getId());

        MockHttpSession session = login(reader.getUserId());
        mvc.perform(get("/reader/loans").session(session)
                        .param("readerId", otherReader.getUserId().toString()).param("state", "ALL"))
                .andExpect(status().isOk()).andExpect(view().name("loan/reader-list"))
                .andExpect(content().string(containsString(ownTitle)))
                .andExpect(content().string(containsString(returnedTitle)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(otherTitle))));
        mvc.perform(get("/reader/loans").session(session).param("state", "CURRENT"))
                .andExpect(status().isOk()).andExpect(content().string(containsString(ownTitle)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(returnedTitle))));
        mvc.perform(get("/reader/loans").session(session).param("state", "RETURNED"))
                .andExpect(status().isOk()).andExpect(content().string(containsString(returnedTitle)))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(ownTitle))));

        mvc.perform(get("/staff/loans").session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanFilterAllRecordsAndNavigatePages() throws Exception {
        List<BookCopy> created = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            ReaderProfile borrower = index == 0 ? reader : reader();
            String title = "分页图书-" + index + "-" + token();
            BookCopy copy = copy(title);
            created.add(copy);
            titles.add(title);
            checkout.checkout(librarian.getId(), UserRole.LIBRARIAN, borrower.getReaderNo(), copy.getBarcode());
        }
        MockHttpSession session = login(librarian.getId());

        mvc.perform(get("/staff/loans").session(session).param("state", "ALL"))
                .andExpect(status().isOk()).andExpect(view().name("loan/staff-list"))
                .andExpect(content().string(containsString("1</span> / <span>2</span> 页")))
                .andExpect(content().string(containsString(titles.get(10))));
        mvc.perform(get("/staff/loans").session(session).param("state", "ALL").param("page", "1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("2</span> / <span>2</span> 页")))
                .andExpect(content().string(containsString(titles.get(0))));
        String today = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();
        mvc.perform(get("/staff/loans").session(session).param("state", "ALL")
                        .param("fromDate", today).param("toDate", today))
                .andExpect(status().isOk()).andExpect(content().string(containsString(titles.get(10))));
        mvc.perform(get("/staff/loans").session(session).param("state", "ALL")
                        .param("reader", reader.getReaderNo()).param("barcode", created.get(0).getBarcode()))
                .andExpect(status().isOk()).andExpect(content().string(containsString(titles.get(0))))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString(titles.get(10)))));
        mvc.perform(get("/staff/loans").session(session).param("reader", "NO-SUCH-READER"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("没有符合条件的借阅记录")));
        String tomorrow = LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).plusDays(1).toString();
        mvc.perform(get("/staff/loans").session(session).param("fromDate", tomorrow).param("state", "ALL"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("没有符合条件的借阅记录")));
    }

    @Test
    void returnedLateAndCurrentOverdueAreShownAsSeparateStatuses() throws Exception {
        BookCopy dueToday = copy("状态测试A-" + token());
        BookCopy overdue = copy("状态测试B-" + token());
        BookCopy lateReturn = copy("状态测试C-" + token());
        checkout.checkout(librarian.getId(), UserRole.LIBRARIAN, reader.getReaderNo(), dueToday.getBarcode());
        Loan openOverdue = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader().getReaderNo(), overdue.getBarcode());
        Loan willReturnLate = checkout.checkout(librarian.getId(), UserRole.LIBRARIAN,
                reader().getReaderNo(), lateReturn.getBarcode());
        LocalDate today = LocalDate.now(java.time.Clock.systemUTC().withZone(java.time.ZoneId.of("Asia/Shanghai")));
        jdbc.update("UPDATE loan SET due_date = ? WHERE copy_id = ?", today, dueToday.getId());
        jdbc.update("UPDATE loan SET due_date = ? WHERE id = ?", today.minusDays(1), openOverdue.getId());
        jdbc.update("UPDATE loan SET due_date = ? WHERE id = ?", today.minusDays(1), willReturnLate.getId());
        returns.returnByLoanId(librarian.getId(), UserRole.LIBRARIAN, willReturnLate.getId());

        MockHttpSession session = login(librarian.getId());
        String currentPage = mvc.perform(get("/staff/loans").session(session).param("state", "CURRENT"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(currentPage.contains("状态测试A-"));
        assertTrue(currentPage.contains("状态测试B-"));
        assertFalse(currentPage.contains("状态测试C-"));
        String returnedPage = mvc.perform(get("/staff/loans").session(session).param("state", "RETURNED"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(returnedPage.contains("状态测试C-"));
        assertFalse(returnedPage.contains("状态测试A-"));
        String page = mvc.perform(get("/staff/loans").session(session).param("state", "ALL"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(1, occurrences(page, "当前逾期"));
        assertEquals(1, occurrences(page, "逾期归还"));
        assertEquals(1, occurrences(page, "借阅中"));
    }

    private AppUser staff() {
        AppUser user = accounts.createUser("loan_query_staff_" + token(), PASSWORD, "查询馆员", UserRole.LIBRARIAN);
        user.markPasswordChanged();
        userIds.add(users.saveAndFlush(user).getId());
        return user;
    }

    private ReaderProfile reader() {
        String suffix = token();
        AppUser user = accounts.createReader(UserRole.ADMIN, "loan_query_reader_" + suffix, PASSWORD,
                "查询读者" + suffix.substring(0, 4), "T15-" + suffix, null);
        user.markPasswordChanged();
        users.saveAndFlush(user);
        userIds.add(user.getId());
        ReaderProfile profile = readers.findByUserId(user.getId()).orElseThrow();
        profileIds.add(profile.getId());
        return profile;
    }

    private BookCopy copy(String title) {
        var book = bookService.create(UserRole.ADMIN, title, "查询测试作者", null, null, null, category.getId(), null);
        bookIds.add(book.getId());
        BookCopy copy = copyService.register(UserRole.ADMIN, book.getId(), "T15-BC-" + token(), "T15 测试架");
        copyIds.add(copy.getId());
        return copy;
    }

    private MockHttpSession login(Long userId) throws Exception {
        AppUser user = users.findById(userId).orElseThrow();
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                        .param("username", user.getLoginName().toUpperCase(Locale.ROOT)).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andReturn().getRequest().getSession(false);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        for (int index = value.indexOf(needle); index >= 0; index = value.indexOf(needle, index + needle.length())) count++;
        return count;
    }

    private static String token() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
