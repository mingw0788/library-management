package com.example.library.catalog;

import com.example.library.account.AccountService;
import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.UserRole;
import com.example.library.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
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
class BookManagementTest {

    private static final String PASSWORD = "book-test-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private BookService bookService;
    @Autowired private BookRepository books;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categories;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private JdbcTemplate jdbcTemplate;

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
        category = categoryService.create(UserRole.ADMIN, "T09 分类-" + token());
        categoryIds.add(category.getId());
    }

    @AfterEach
    void clean() {
        bookIds.forEach(id -> books.findById(id).ifPresent(books::delete));
        books.flush();
        categoryIds.forEach(id -> categories.findById(id).ifPresent(categories::delete));
        categories.flush();
        userIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        userIds.clear();
        categoryIds.clear();
        bookIds.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorAndLibrarianCanOpenAndWriteButReaderCannotForgeRoleOrSkipCsrf() throws Exception {
        mvc.perform(get("/staff/books")).andExpect(status().is3xxRedirection());
        MockHttpSession adminSession = login(admin);
        mvc.perform(get("/staff/books").session(adminSession)).andExpect(status().isOk())
                .andExpect(view().name("catalog/books"));
        MockHttpSession librarianSession = login(librarian);
        mvc.perform(get("/staff/books").session(librarianSession)).andExpect(status().isOk());

        String missingCsrfTitle = "NoCsrf-" + token();
        mvc.perform(post("/staff/books").session(librarianSession)
                        .param("title", missingCsrfTitle).param("author", "作者")
                        .param("categoryId", category.getId().toString()))
                .andExpect(status().isForbidden());
        assertTrue(books.findAll().stream().noneMatch(book -> book.getTitle().equals(missingCsrfTitle)));

        String title = "网页书目-" + token();
        mvc.perform(post("/staff/books").with(csrf()).session(librarianSession)
                        .param("title", title).param("author", "网页作者").param("isbn", "978-0-306-40615-7")
                        .param("publishedDate", "2020-05-06").param("categoryId", category.getId().toString()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/books"));
        Book fromPage = books.findByIsbn("9780306406157").orElseThrow();
        bookIds.add(fromPage.getId());
        assertEquals(LocalDate.of(2020, 5, 6), fromPage.getPublishedDate());
        mvc.perform(get("/staff/books").param("q", title).session(adminSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString(title)));

        MockHttpSession readerSession = login(reader);
        mvc.perform(get("/staff/books?role=LIBRARIAN").session(readerSession)).andExpect(status().isForbidden());
        mvc.perform(post("/staff/books").with(csrf()).session(readerSession)
                        .param("title", "伪造权限").param("author", "作者")
                        .param("categoryId", category.getId().toString()).param("role", "LIBRARIAN"))
                .andExpect(status().isForbidden());
        assertTrue(books.findAll().stream().noneMatch(book -> book.getTitle().equals("伪造权限")));
    }

    @Test
    void isbn10And13NormalizeAndValidateAndNullIsbnMayRepeat() {
        Book isbn10 = create("ISBN10 " + token(), "030 6406-152");
        assertEquals("0306406152", isbn10.getIsbn());
        Book isbn10X = create("ISBN10 X " + token(), "0-8044-2957-X");
        assertEquals("080442957X", isbn10X.getIsbn());
        Book isbn13 = create("ISBN13 " + token(), " 978-0-306-40615-7 ");
        assertEquals("9780306406157", isbn13.getIsbn());
        assertThrows(IllegalArgumentException.class, () -> create("Bad ISBN", "9780306406158"));
        assertThrows(IllegalArgumentException.class, () -> create("Bad ISBN 10", "030640615X"));
        assertThrows(IllegalArgumentException.class, () -> create("Bad ISBN prefix", "9770000000003"));
        assertThrows(IllegalArgumentException.class, () -> create("Bad ISBN length", "12345"));
        assertThrows(IllegalArgumentException.class, () -> create("Bad ISBN separators", "---"));
        assertThrows(IllegalArgumentException.class, () -> create("Duplicate ISBN", "978 0306406157"));

        Book noIsbn1 = create("No ISBN one " + token(), "  ");
        Book noIsbn2 = create("No ISBN two " + token(), null);
        assertNull(noIsbn1.getIsbn());
        assertNull(noIsbn2.getIsbn());
        assertNotEquals(noIsbn1.getId(), noIsbn2.getId());
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO book (category_id, title, author, isbn) VALUES (?, ?, ?, ?)",
                category.getId(), "数据库重复ISBN", "作者", "9780306406157"));
    }

    @Test
    void fieldAndCategoryRulesAreEnforcedAndCategoryDeleteIsBlockedByMysqlForeignKey() {
        assertThrows(IllegalArgumentException.class, () -> create("   ", null));
        assertThrows(IllegalArgumentException.class,
                () -> createDetails("标题", "作者", "x".repeat(201), null, null, category.getId(), null));
        assertThrows(IllegalArgumentException.class,
                () -> createDetails("标题", "x".repeat(201), null, null, null, category.getId(), null));
        assertThrows(IllegalArgumentException.class,
                () -> createDetails("标题", "作者", null, null, null, category.getId(), "x".repeat(2001)));
        assertThrows(IllegalArgumentException.class,
                () -> createDetails("标题", "作者", null, null, null, -5L, null));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "INSERT INTO book (category_id, title, author) VALUES (?, ?, ?)",
                -5L, "非法分类外键", "作者"));

        Book book = create("分类引用 " + token(), "9780306406157");
        assertThrows(RuntimeException.class, () -> categoryService.delete(UserRole.ADMIN, category.getId()));
        assertTrue(categories.findById(category.getId()).isPresent());
        assertEquals(category.getId(), book.getCategoryId());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> bookService.create(UserRole.READER, "越权", "作者", null, null, null, category.getId(), null));
    }

    @Test
    void versionedEditsRejectStaleChangesAndArchivedBooksStayOutOfCatalogResults() {
        Book created = create("并发测试书名 " + token(), "0306406152");
        Book edited = bookService.update(UserRole.LIBRARIAN, created.getId(), created.getVersion(),
                "更新后的书名", "新作者", " 新出版社 ", LocalDate.of(2019, 1, 2), "0306406152",
                category.getId(), " 新简介 ");
        assertEquals(created.getVersion() + 1, edited.getVersion());
        assertEquals("新出版社", edited.getPublisher());
        assertEquals("新简介", edited.getDescription());
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> bookService.update(UserRole.ADMIN, edited.getId(), created.getVersion(),
                        "过期编辑", "作者", null, null, null, category.getId(), null));

        Book concurrent = create("并发数据库测试 " + token(), null);
        Book firstRead = books.findById(concurrent.getId()).orElseThrow();
        Book staleRead = books.findById(concurrent.getId()).orElseThrow();
        firstRead.update(category.getId(), "第一个工作人员提交", "作者甲", null, null, null, null);
        books.saveAndFlush(firstRead);
        staleRead.update(category.getId(), "旧版本工作人员提交", "作者乙", null, null, null, null);
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> books.saveAndFlush(staleRead));

        bookService.archive(UserRole.ADMIN, edited.getId());
        Book archived = books.findById(edited.getId()).orElseThrow();
        assertEquals(BookStatus.ARCHIVED, archived.getStatus());
        assertFalse(bookService.listActiveBooksForCatalog().stream().anyMatch(book -> book.getId().equals(archived.getId())));
        assertThrows(IllegalArgumentException.class,
                () -> bookService.update(UserRole.ADMIN, archived.getId(), archived.getVersion(),
                        "不可编辑", "作者", null, null, null, category.getId(), null));
        assertThrows(IllegalArgumentException.class, () -> bookService.archive(UserRole.ADMIN, archived.getId()));
    }

    private Book create(String title, String isbn) {
        return createDetails(title, "测试作者", null, null, isbn, category.getId(), null);
    }

    private Book createDetails(String title, String author, String publisher, LocalDate publishedDate,
                               String isbn, Long categoryId, String description) {
        Book book = bookService.create(UserRole.ADMIN, title, author, publisher, publishedDate,
                isbn, categoryId, description);
        bookIds.add(book.getId());
        return book;
    }

    private Fixture fixture(UserRole role) {
        String loginName = "book_" + token();
        AppUser user = accounts.createUser(loginName, PASSWORD, "书目测试账号", role);
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
