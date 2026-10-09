package com.example.library.catalog;

import com.example.library.account.AccountService;
import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BookCopyManagementTest {

    private static final String PASSWORD = "copy-test-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private BookCopyService copyService;
    @Autowired private BookCopyRepository copies;
    @Autowired private BookService bookService;
    @Autowired private BookRepository books;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categories;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private JdbcTemplate jdbc;

    private final List<Long> copyIds = new ArrayList<>();
    private final List<Long> bookIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private Fixture admin;
    private Fixture librarian;
    private Fixture reader;
    private Category category;

    @BeforeEach
    void prepare() {
        admin = fixture(UserRole.ADMIN);
        librarian = fixture(UserRole.LIBRARIAN);
        reader = fixture(UserRole.READER);
        category = categoryService.create(UserRole.ADMIN, "T10 分类-" + token());
        categoryIds.add(category.getId());
    }

    @AfterEach
    void clean() {
        copyIds.forEach(id -> copies.findById(id).ifPresent(copies::delete));
        copies.flush();
        bookIds.forEach(id -> books.findById(id).ifPresent(books::delete));
        books.flush();
        categoryIds.forEach(id -> categories.findById(id).ifPresent(categories::delete));
        categories.flush();
        userIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        copyIds.clear();
        bookIds.clear();
        categoryIds.clear();
        userIds.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void oneBookCanHaveMultipleCopiesButBarcodeAndBookReferencesAreConstrained() {
        Book book = book("多册书目 " + token());
        BookCopy first = register(book, " BAR-" + token() + " ", " A-01 ");
        BookCopy second = register(book, "BAR-" + token(), null);
        assertEquals(book.getId(), first.getBookId());
        assertEquals(book.getId(), second.getBookId());
        assertEquals(BookCopyStatus.AVAILABLE, first.getStatus());
        assertEquals("A-01", first.getShelfLocation());
        assertNull(second.getShelfLocation());
        assertThrows(IllegalArgumentException.class,
                () -> copyService.register(UserRole.ADMIN, book.getId(), first.getBarcode().toLowerCase(Locale.ROOT), null));
        assertThrows(IllegalArgumentException.class,
                () -> copyService.register(UserRole.ADMIN, -99L, "INVALID-BOOK", null));
        assertThrows(IllegalArgumentException.class,
                () -> copyService.register(UserRole.ADMIN, book.getId(), "   ", null));
        assertThrows(IllegalArgumentException.class,
                () -> copyService.register(UserRole.ADMIN, book.getId(), "x".repeat(51), null));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO book_copy (book_id, barcode, status) VALUES (?, ?, 'AVAILABLE')",
                -99L, "FK-" + token()));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO book_copy (book_id, barcode, status) VALUES (?, ?, 'UNKNOWN')",
                book.getId(), "BAD-STATE-" + token()));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO book_copy (book_id, barcode, status) VALUES (?, ?, 'AVAILABLE')",
                book.getId(), first.getBarcode()));
    }

    @Test
    void onlyActiveBooksCanReceiveCopiesAndArchiveWaitsUntilEveryCopyIsWithdrawn() {
        Book book = book("归档保护 " + token());
        BookCopy copy = register(book, "ARCHIVE-" + token(), null);
        assertThrows(IllegalArgumentException.class, () -> bookService.archive(UserRole.ADMIN, book.getId()));
        assertEquals(BookStatus.ACTIVE, books.findById(book.getId()).orElseThrow().getStatus());

        copyService.withdraw(UserRole.LIBRARIAN, copy.getId());
        assertEquals(BookCopyStatus.WITHDRAWN, copies.findById(copy.getId()).orElseThrow().getStatus());
        assertThrows(IllegalArgumentException.class, () -> copyService.withdraw(UserRole.ADMIN, copy.getId()));
        bookService.archive(UserRole.ADMIN, book.getId());
        assertEquals(BookStatus.ARCHIVED, books.findById(book.getId()).orElseThrow().getStatus());
        assertThrows(IllegalArgumentException.class,
                () -> copyService.register(UserRole.ADMIN, book.getId(), "AFTER-ARCHIVE-" + token(), null));

        Book loanBook = book("借出状态保护 " + token());
        BookCopy simulatedLoan = register(loanBook, "ON-LOAN-" + token(), null);
        jdbc.update("UPDATE book_copy SET status = 'ON_LOAN' WHERE id = ?", simulatedLoan.getId());
        assertThrows(IllegalArgumentException.class,
                () -> copyService.withdraw(UserRole.ADMIN, simulatedLoan.getId()));
        assertThrows(IllegalArgumentException.class, () -> bookService.archive(UserRole.ADMIN, loanBook.getId()));
        assertEquals(BookCopyStatus.ON_LOAN, copies.findById(simulatedLoan.getId()).orElseThrow().getStatus());
    }

    @Test
    void inventorySummaryCountsEachStatusAndUpdatesAfterWithdrawal() {
        Book book = book("数量汇总 " + token());
        BookCopy available = register(book, "COUNT-A-" + token(), null);
        BookCopy onLoan = register(book, "COUNT-L-" + token(), null);
        BookCopy withdrawn = register(book, "COUNT-W-" + token(), null);
        jdbc.update("UPDATE book_copy SET status = 'ON_LOAN' WHERE id = ?", onLoan.getId());
        copyService.withdraw(UserRole.ADMIN, withdrawn.getId());

        BookCopyInventory inventory = copyService.inventoryByBookIds(List.of(book.getId())).get(book.getId());
        assertEquals(new BookCopyInventory(1, 1, 1), inventory);
        assertEquals(3, inventory.total());
        copyService.withdraw(UserRole.ADMIN, available.getId());
        BookCopyInventory changed = copyService.inventoryByBookIds(List.of(book.getId())).get(book.getId());
        assertEquals(new BookCopyInventory(0, 1, 2), changed);
    }

    @Test
    void staffCanManageCopiesButReaderAndCsrfFreeWritesAreRejected() throws Exception {
        Book book = book("网页副本 " + token());
        MockHttpSession adminSession = login(admin);
        mvc.perform(get("/staff/copies").session(adminSession)).andExpect(status().isOk())
                .andExpect(view().name("catalog/copies"));
        mvc.perform(get("/staff/books").session(adminSession)).andExpect(status().isOk())
                .andExpect(content().string(containsString("副本数量")));

        MockHttpSession librarianSession = login(librarian);
        String barcode = "WEB-" + token();
        mvc.perform(post("/staff/copies").session(librarianSession).param("bookId", book.getId().toString())
                        .param("barcode", barcode))
                .andExpect(status().isForbidden());
        mvc.perform(post("/staff/copies").with(csrf()).session(librarianSession)
                        .param("bookId", book.getId().toString()).param("barcode", barcode)
                        .param("shelfLocation", "A-02"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/copies"));
        BookCopy created = copies.findByBarcode(barcode).orElseThrow();
        copyIds.add(created.getId());
        assertEquals(BookCopyStatus.AVAILABLE, created.getStatus());
        mvc.perform(get("/staff/copies").param("q", barcode).session(adminSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString(barcode)))
                .andExpect(content().string(containsString("A-02")));
        mvc.perform(post("/staff/copies/{id}/withdraw", created.getId()).with(csrf()).session(librarianSession))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/copies"));
        assertEquals(BookCopyStatus.WITHDRAWN, copies.findById(created.getId()).orElseThrow().getStatus());

        MockHttpSession readerSession = login(reader);
        mvc.perform(get("/staff/copies?role=LIBRARIAN").session(readerSession)).andExpect(status().isForbidden());
        mvc.perform(post("/staff/copies").with(csrf()).session(readerSession)
                        .param("bookId", book.getId().toString()).param("barcode", "FORGED-" + token())
                        .param("role", "LIBRARIAN"))
                .andExpect(status().isForbidden());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> copyService.register(UserRole.READER, book.getId(), "DIRECT-" + token(), null));
    }

    private Book book(String title) {
        Book created = bookService.create(UserRole.ADMIN, title, "作者", null, null, null, category.getId(), null);
        bookIds.add(created.getId());
        return created;
    }

    private BookCopy register(Book book, String barcode, String location) {
        BookCopy created = copyService.register(UserRole.ADMIN, book.getId(), barcode, location);
        copyIds.add(created.getId());
        return created;
    }

    private Fixture fixture(UserRole role) {
        String loginName = "copy_" + token();
        AppUser user = accounts.createUser(loginName, PASSWORD, "副本测试账号", role);
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

    private static String token() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private record Fixture(Long id, String loginName) {}
}
