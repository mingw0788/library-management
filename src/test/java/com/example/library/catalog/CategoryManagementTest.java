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
class CategoryManagementTest {

    private static final String PASSWORD = "category-test-pass-123";

    @Autowired private MockMvc mvc;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categories;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final List<Long> categoryIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private Fixture admin;
    private Fixture librarian;
    private Fixture reader;

    @BeforeEach
    void prepare() {
        admin = fixture(UserRole.ADMIN);
        librarian = fixture(UserRole.LIBRARIAN);
        reader = fixture(UserRole.READER);
    }

    @AfterEach
    void clean() {
        categoryIds.forEach(id -> categories.findById(id).ifPresent(categories::delete));
        categories.flush();
        userIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        userIds.clear();
        categoryIds.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void librarianAndAdminCanManageButReaderAndAnonymousCannot() throws Exception {
        mvc.perform(get("/staff/categories")).andExpect(status().is3xxRedirection());
        MockHttpSession adminSession = login(admin);
        mvc.perform(get("/staff/categories").session(adminSession)).andExpect(status().isOk())
                .andExpect(view().name("catalog/categories"));
        MockHttpSession librarianSession = login(librarian);
        mvc.perform(get("/staff/categories?role=ADMIN").session(librarianSession)).andExpect(status().isOk());
        mvc.perform(post("/staff/categories").with(csrf()).session(librarianSession).param("name", "网页创建"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/staff/categories"));
        Category createdByLibrarian = categories.findByName("网页创建").orElseThrow();
        categoryIds.add(createdByLibrarian.getId());
        String withoutCsrf = "csrf_" + token();
        mvc.perform(post("/staff/categories").session(librarianSession).param("name", withoutCsrf))
                .andExpect(status().isForbidden());
        assertTrue(categories.findByName(withoutCsrf).isEmpty());

        MockHttpSession readerSession = login(reader);
        mvc.perform(get("/staff/categories?role=LIBRARIAN").session(readerSession)).andExpect(status().isForbidden());
        mvc.perform(post("/staff/categories").with(csrf()).session(readerSession)
                        .param("name", "伪造权限").param("role", "LIBRARIAN"))
                .andExpect(status().isForbidden());
        assertTrue(categories.findByName("伪造权限").isEmpty());
    }

    @Test
    void createRenameAndSearchNormalizeNamesAndEnforceUniqueCaseInsensitiveNames() throws Exception {
        String suffix = token();
        String name = " 文学-" + suffix + " ";
        Category category = categoryService.create(UserRole.ADMIN, name);
        categoryIds.add(category.getId());
        assertEquals(name.trim(), category.getName());
        assertThrows(IllegalArgumentException.class, () -> categoryService.create(UserRole.LIBRARIAN,
                name.trim().toUpperCase(Locale.ROOT)));
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("INSERT INTO category (name) VALUES (?)", name.trim().toUpperCase(Locale.ROOT)));
        assertThrows(DataAccessException.class,
                () -> jdbcTemplate.update("INSERT INTO category (name) VALUES ('   ')"));
        assertThrows(IllegalArgumentException.class, () -> categoryService.create(UserRole.ADMIN, "  "));
        assertThrows(IllegalArgumentException.class, () -> categoryService.create(UserRole.ADMIN, "字".repeat(101)));

        Category sameNameRename = categoryService.rename(UserRole.LIBRARIAN, category.getId(), "  " + category.getName() + " ");
        assertEquals(category.getName(), sameNameRename.getName());
        Category another = categoryService.create(UserRole.ADMIN, "历史-" + token());
        categoryIds.add(another.getId());
        assertThrows(IllegalArgumentException.class,
                () -> categoryService.rename(UserRole.ADMIN, another.getId(), category.getName().toUpperCase(Locale.ROOT)));
        assertThrows(IllegalArgumentException.class, () -> categoryService.rename(UserRole.ADMIN, -42L, "不存在"));
        assertThrows(IllegalArgumentException.class, () -> categoryService.delete(UserRole.ADMIN, -42L));

        String query = suffix.substring(0, 5).toUpperCase(Locale.ROOT);
        List<Category> matches = categoryService.search(UserRole.LIBRARIAN, query);
        assertTrue(matches.stream().anyMatch(item -> item.getId().equals(category.getId())));
        assertTrue(matches.stream().noneMatch(item -> item.getId().equals(another.getId())));
        mvc.perform(get("/staff/categories").param("q", query).session(login(librarian)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(category.getName())));
        mvc.perform(get("/staff/categories").param("q", "x".repeat(101)).session(login(librarian)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("查询词不能超过 100 个字符")));
        assertThrows(IllegalArgumentException.class,
                () -> categoryService.search(UserRole.ADMIN, "x".repeat(101)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> categoryService.create(UserRole.READER, "reader-created"));
    }

    @Test
    void unusedCategoryCanBeDeleted() {
        Category category = categoryService.create(UserRole.ADMIN, "待删除-" + token());
        categoryService.delete(UserRole.LIBRARIAN, category.getId());
        assertTrue(categories.findById(category.getId()).isEmpty());
    }

    @Test
    void duplicateCategoryShowsAnInlineErrorAndEscapesRetainedInput() throws Exception {
        String maliciousName = "<script>alert(1)</script>";
        Category existing = categoryService.create(UserRole.ADMIN, maliciousName);
        categoryIds.add(existing.getId());
        MockHttpSession librarianSession = login(librarian);

        var failed = mvc.perform(post("/staff/categories").with(csrf()).session(librarianSession)
                        .param("name", maliciousName))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("formKey", "CATEGORY_CREATE"))
                .andExpect(flash().attributeExists("fieldErrors")).andReturn();
        @SuppressWarnings("unchecked")
        var fieldErrors = (java.util.Map<String, String>) failed.getFlashMap().get("fieldErrors");
        assertTrue(fieldErrors.get("name").contains("已存在"));

        var session = (MockHttpSession) failed.getRequest().getSession(false);
        String html = mvc.perform(get("/staff/categories").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"));
        assertFalse(html.contains(maliciousName));
        assertTrue(html.contains("CATEGORY_CREATE-name-error"));
    }

    private Fixture fixture(UserRole role) {
        String loginName = "cat_" + token();
        AppUser user = accounts.createUser(loginName, PASSWORD, "分类测试账号", role);
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
