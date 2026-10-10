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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogSearchTest {

    private static final String PASSWORD = "catalog-test-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private CatalogService catalog;
    @Autowired private BookService bookService;
    @Autowired private BookCopyService copyService;
    @Autowired private BookRepository books;
    @Autowired private BookCopyRepository copies;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categories;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private JdbcTemplate jdbc;

    private final List<Long> copyIds = new ArrayList<>();
    private final List<Long> bookIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private Category fiction;
    private Category history;
    private Fixture admin;
    private Fixture reader;
    private Fixture librarian;

    @BeforeEach
    void prepare() {
        admin = fixture(UserRole.ADMIN);
        reader = fixture(UserRole.READER);
        librarian = fixture(UserRole.LIBRARIAN);
        fiction = categoryService.create(UserRole.ADMIN, "T11 小说-" + token());
        history = categoryService.create(UserRole.ADMIN, "T11 历史-" + token());
        categoryIds.add(fiction.getId());
        categoryIds.add(history.getId());
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
    void keywordCategoryAndCombinedFiltersReturnOnlyMatchingActiveBooks() {
        String token = token();
        Book first = book("T11 Alpha " + token, "Author One", "9780306406157", fiction, LocalDate.of(2020, 1, 1));
        Book second = book("T11 Beta " + token, "Ocean Writer", null, fiction, LocalDate.of(2021, 1, 1));
        Book third = book("T11 Gamma " + token, "History Writer", null, history, LocalDate.of(2019, 1, 1));
        Book archived = book("T11 Archived " + token, "Archived Author", null, fiction, null);
        bookService.archive(UserRole.ADMIN, archived.getId());

        assertTrue(ids(catalog.search("Alpha " + token, null, 0, 20, "titleAsc")).contains(first.getId()));
        assertTrue(ids(catalog.search("Ocean Writer", null, 0, 20, "titleAsc")).contains(second.getId()));
        assertTrue(ids(catalog.search("9780306406157", null, 0, 20, "titleAsc")).contains(first.getId()));
        assertEquals(List.of(first.getId(), second.getId()), ids(
                catalog.search(token, fiction.getId(), 0, 20, "titleAsc")));
        assertEquals(List.of(second.getId()), ids(
                catalog.search("Writer", fiction.getId(), 0, 20, "titleAsc")));
        bookService.archive(UserRole.ADMIN, third.getId());
        assertFalse(ids(catalog.search(token, null, 0, 20, "titleAsc")).contains(third.getId()));
        assertFalse(ids(catalog.search(token, null, 0, 20, "titleAsc")).contains(archived.getId()));
    }

    @Test
    void detailRoundTripRetainsEncodedFiltersSortAndActualPage() throws Exception {
        String keyword = "中文 & 导航 " + token();
        book(keyword + " A", "导航作者", null, fiction, null);
        book(keyword + " B", "导航作者", null, fiction, null);
        MockHttpSession session = login(reader);
        var search = mvc.perform(get("/catalog").session(session).param("q", keyword)
                        .param("categoryId", fiction.getId().toString()).param("sort", "titleDesc")
                        .param("size", "1").param("page", "99"))
                .andExpect(status().isOk()).andReturn();
        var page = (org.springframework.data.domain.Page<?>) search.getModelAndView().getModel().get("booksPage");
        assertEquals(1, page.getNumber());
        Book selected = (Book) page.getContent().getFirst();
        var matcher = java.util.regex.Pattern.compile("href=\"(/catalog/" + selected.getId() + "\\?[^\"]+)\"")
                .matcher(search.getResponse().getContentAsString());
        assertTrue(matcher.find());
        String href = org.springframework.web.util.HtmlUtils.htmlUnescape(matcher.group(1));
        var detail = mvc.perform(get(java.net.URI.create(href)).session(session))
                .andExpect(status().isOk()).andReturn();
        String back = (String) detail.getModelAndView().getModel().get("catalogReturnUrl");
        assertTrue(back.startsWith("/catalog?"));
        assertTrue(back.contains("%26"));
        assertTrue(back.contains("page=1"));
        var result = mvc.perform(get(java.net.URI.create(back)).session(session))
                .andExpect(status().isOk()).andReturn();
        assertEquals(keyword, result.getModelAndView().getModel().get("query"));
        assertEquals(fiction.getId(), result.getModelAndView().getModel().get("categoryId"));
        assertEquals("titleDesc", result.getModelAndView().getModel().get("sort"));
        var restored = (org.springframework.data.domain.Page<?>) result.getModelAndView().getModel().get("booksPage");
        assertEquals(1, restored.getSize());
        assertEquals(1, restored.getNumber());
        assertEquals(selected.getId(), ((Book) restored.getContent().getFirst()).getId());
    }

    @Test
    void malformedBrowseContextFallsBackWithoutBreakingDetailsOrAllowingExternalReturn() throws Exception {
        Book book = book("返回校验 " + token(), "作者", null, fiction, null);
        MockHttpSession session = login(reader);
        for (String[] invalid : List.of(new String[]{"page", "-1"}, new String[]{"page", "100001"},
                new String[]{"size", "0"}, new String[]{"size", "101"}, new String[]{"size", "oops"},
                new String[]{"categoryId", "-1"}, new String[]{"categoryId", "oops"},
                new String[]{"sort", "https://example.com"}, new String[]{"q", "a".repeat(101)},
                new String[]{"q", "test\u0001control"})) {
            mvc.perform(get("/catalog/{id}", book.getId()).session(session).param(invalid[0], invalid[1]))
                    .andExpect(status().isOk()).andExpect(model().attribute("catalogReturnUrl", "/catalog"));
        }
        mvc.perform(get("/catalog/{id}", book.getId()).session(session)
                        .param("returnUrl", "https://example.com").param("redirect", "//example.com"))
                .andExpect(status().isOk()).andExpect(model().attribute("catalogReturnUrl", "/catalog"));
    }

    @Test
    void pagingSortsStablyClampsPastEndAndLimitsPageSize() {
        String token = "PAGE" + token();
        Book alpha = book("A " + token, "Same Author", null, fiction, LocalDate.of(2020, 1, 1));
        Book beta = book("B " + token, "Same Author", null, fiction, LocalDate.of(2022, 1, 1));
        Book gamma = book("C " + token, "Same Author", null, fiction, LocalDate.of(2019, 1, 1));

        var first = catalog.search(token, fiction.getId(), 0, 1, "titleAsc").books();
        var second = catalog.search(token, fiction.getId(), 1, 1, "titleAsc").books();
        var last = catalog.search(token, fiction.getId(), 100, 1, "titleAsc").books();
        assertEquals(List.of(alpha.getId()), ids(first));
        assertEquals(List.of(beta.getId()), ids(second));
        assertEquals(List.of(gamma.getId()), ids(last));
        assertEquals(2, last.getNumber());
        assertEquals(100, catalog.search(token, fiction.getId(), 0, 500, "authorAsc").books().getSize());
        assertEquals(List.of(alpha.getId(), beta.getId(), gamma.getId()),
                ids(catalog.search(token, fiction.getId(), 0, 20, "authorAsc").books()));
        assertEquals(List.of(beta.getId(), alpha.getId(), gamma.getId()),
                ids(catalog.search(token, fiction.getId(), 0, 20, "dateDesc").books()));
        assertThrows(IllegalArgumentException.class, () -> catalog.search(token, null, -1, 20, "titleAsc"));
        assertThrows(IllegalArgumentException.class, () -> catalog.search(token, null, 0, 0, "titleAsc"));
        assertThrows(IllegalArgumentException.class, () -> catalog.search(token, null, 0, 20, "title;drop"));
        assertThrows(IllegalArgumentException.class, () -> catalog.search(token, 999_999L, 0, 20, "titleAsc"));
        assertThrows(IllegalArgumentException.class,
                () -> catalog.search("x".repeat(101), null, 0, 20, "titleAsc"));
        assertTrue(catalog.search("NO-MATCH-" + token, null, 0, 20, "titleAsc").books().isEmpty());
    }

    @Test
    void catalogPageShowsACompactWindowForLargeResultSets() throws Exception {
        String marker = "WINDOW" + token();
        for (int index = 0; index < 9; index++) {
            book(marker + "-" + index, "窗口测试作者", null, fiction, null);
        }
        MockHttpSession readerSession = login(reader);

        String html = mvc.perform(get("/catalog").param("q", marker).param("size", "1")
                        .param("page", "4").session(readerSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString("aria-label=\"检索结果分页\"")))
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("…"));
        assertTrue(html.contains("aria-current=\"page\""));
        assertFalse(html.contains("page=0&amp;"));
    }

    @Test
    void detailsShowAvailabilityAndCirculatingCopiesButHideWithdrawnAndArchivedBooks() {
        Book book = book("T11 馆藏详情 " + token(), "详情作者", null, fiction, null);
        BookCopy available = copy(book, "T11-A-" + token(), "A-01");
        BookCopy onLoan = copy(book, "T11-L-" + token(), "B-02");
        BookCopy withdrawn = copy(book, "T11-W-" + token(), "C-03");
        jdbc.update("UPDATE book_copy SET status = 'ON_LOAN' WHERE id = ?", onLoan.getId());
        copyService.withdraw(UserRole.ADMIN, withdrawn.getId());

        CatalogBookDetail detail = catalog.detail(book.getId());
        assertEquals(fiction.getName(), detail.category().getName());
        assertEquals(1, detail.availableCount());
        assertEquals(2, detail.copies().size());
        assertTrue(detail.copies().stream().anyMatch(copy -> copy.status() == BookCopyStatus.AVAILABLE
                && copy.shelfLocation().equals("A-01")));
        assertTrue(detail.copies().stream().anyMatch(copy -> copy.status() == BookCopyStatus.ON_LOAN
                && copy.shelfLocation().equals("B-02")));
        assertFalse(detail.copies().stream().anyMatch(copy -> copy.shelfLocation().equals("C-03")));

        Book archived = book("T11 归档详情测试 " + token(), "归档作者", null, fiction, null);
        bookService.archive(UserRole.ADMIN, archived.getId());
        assertThrows(IllegalArgumentException.class, () -> catalog.detail(archived.getId()));
        assertThrows(IllegalArgumentException.class, () -> catalog.detail(-1L));
    }

    @Test
    void everyLoggedInRoleCanReadCatalogButCannotOpenStaffCopyMaintenance() throws Exception {
        Book book = book("T11 页面目录 " + token(), "网页作者", null, fiction, null);
        MockHttpSession readerSession = login(reader);
        mvc.perform(get("/catalog")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/catalog").session(readerSession))
                .andExpect(status().isOk()).andExpect(view().name("catalog/search"))
                .andExpect(content().string(containsString(book.getTitle())))
                .andExpect(content().string(containsString(fiction.getName())));
        mvc.perform(get("/catalog/{id}", book.getId()).session(readerSession))
                .andExpect(status().isOk()).andExpect(view().name("catalog/detail"))
                .andExpect(content().string(containsString("可借")));
        mvc.perform(get("/catalog/{id}", 9_999_999L).session(readerSession))
                .andExpect(status().isNotFound()).andExpect(view().name("catalog/not-found"));
        mvc.perform(get("/catalog/{id}", book.getId()).session(readerSession).param("role", "LIBRARIAN"))
                .andExpect(status().isOk());
        mvc.perform(get("/staff/copies").session(readerSession)).andExpect(status().isForbidden());
        mvc.perform(post("/staff/copies").with(csrf()).session(readerSession)
                        .param("bookId", book.getId().toString()).param("barcode", "FORGE-" + token())
                        .param("role", "LIBRARIAN"))
                .andExpect(status().isForbidden());

        MockHttpSession adminSession = login(admin);
        mvc.perform(get("/catalog").session(adminSession)).andExpect(status().isOk());
        MockHttpSession librarianSession = login(librarian);
        mvc.perform(get("/catalog").param("q", "no-match").param("categoryId", fiction.getId().toString())
                        .param("sort", "titleAsc").param("size", "500").session(librarianSession))
                .andExpect(status().isOk()).andExpect(content().string(containsString("没有找到符合条件")));
    }

    private List<Long> ids(org.springframework.data.domain.Page<Book> page) {
        return page.getContent().stream().map(Book::getId).toList();
    }

    private List<Long> ids(CatalogSearchResult result) { return ids(result.books()); }

    private Book book(String title, String author, String isbn, Category category, LocalDate date) {
        Book created = bookService.create(UserRole.ADMIN, title, author, null, date, isbn, category.getId(), null);
        bookIds.add(created.getId());
        return created;
    }

    private BookCopy copy(Book book, String barcode, String location) {
        BookCopy created = copyService.register(UserRole.ADMIN, book.getId(), barcode, location);
        copyIds.add(created.getId());
        return created;
    }

    private Fixture fixture(UserRole role) {
        String loginName = "catalog_" + token();
        AppUser user = accounts.createUser(loginName, PASSWORD, "目录测试账号", role);
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
