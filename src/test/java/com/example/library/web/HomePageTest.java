package com.example.library.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import com.example.library.account.*;
import com.example.library.catalog.*;
import com.example.library.security.AppUserPrincipal;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HomePageTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private CatalogService catalog;
    @Autowired private CategoryService categories;
    @Autowired private BookService books;
    @Autowired private BookCopyService copies;

    @Test
    void anonymousHomeInvitesLoginWithoutExposingCatalogueData() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeDoesNotExist("homeCatalog", "homeInventory"))
                .andExpect(content().string(containsString("登录并查找图书")))
                .andExpect(content().string(not(containsString("class=\"home-book-card\""))))
                .andExpect(content().string(not(containsString("href=\"/staff"))));
        mockMvc.perform(get("/catalog")).andExpect(status().is3xxRedirection());
    }

    @Test
    void homeShowsSixActiveBooksAndExcludesWithdrawnInventory() throws Exception {
        var baseline = catalog.overview();
        long titlesBefore = catalog.search(null, null, 0, 6, "titleAsc").books().getTotalElements();
        var category = categories.create(UserRole.ADMIN, "首页测试" + token());
        String marker = "!home" + token();
        Book first = null;
        for (int i = 0; i < 7; i++) {
            Book book = books.create(UserRole.ADMIN, marker + i, "测试作者", null, null, null, category.getId(), null);
            if (first == null) first = book;
        }
        copies.register(UserRole.ADMIN, first.getId(), token(), "A区");
        var withdrawn = copies.register(UserRole.ADMIN, first.getId(), token(), "A区");
        copies.withdraw(UserRole.ADMIN, withdrawn.getId());
        var archived = books.create(UserRole.ADMIN, marker + "archived", "历史作者", null, null, null, category.getId(), null);
        var historical = copies.register(UserRole.ADMIN, archived.getId(), token(), "B区");
        copies.withdraw(UserRole.ADMIN, historical.getId());
        books.archive(UserRole.ADMIN, archived.getId());
        var result = mockMvc.perform(get("/").with(user(principal(UserRole.READER))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(archived.getTitle())))).andReturn();
        assertEquals(6, java.util.regex.Pattern.compile("class=\"home-book-card\"")
                .matcher(result.getResponse().getContentAsString()).results().count());
        var pageModel = result.getModelAndView().getModel();
        var homeCatalog = (CatalogSearchResult) pageModel.get("homeCatalog");
        var inventory = (CatalogOverview) pageModel.get("homeInventory");
        assertEquals(titlesBefore + 7, homeCatalog.books().getTotalElements());
        assertTrue(homeCatalog.books().stream().allMatch(book -> book.getStatus() == BookStatus.ACTIVE));
        assertEquals(baseline.circulatingCopies() + 1, inventory.circulatingCopies());
        assertEquals(baseline.availableCopies() + 1, inventory.availableCopies());
    }

    @Test
    void navigationOffersOnlyTheCurrentRolesOperations() throws Exception {
        for (UserRole role : UserRole.values()) {
            var result = mockMvc.perform(get("/").with(user(principal(role)))).andExpect(status().isOk());
            if (role == UserRole.READER) {
                result.andExpect(content().string(containsString("href=\"/reader/reservations\"")))
                        .andExpect(content().string(not(containsString("href=\"/staff/loans/checkout\""))))
                        .andExpect(content().string(not(containsString("href=\"/admin/accounts\""))));
            } else {
                result.andExpect(content().string(containsString("href=\"/staff/loans/checkout\"")))
                        .andExpect(content().string(containsString("href=\"/staff/reservations\"")))
                        .andExpect(content().string(not(containsString("href=\"/reader/reservations\""))));
                if (role == UserRole.ADMIN) result.andExpect(content().string(containsString("href=\"/admin/accounts\"")));
                else result.andExpect(content().string(not(containsString("href=\"/admin/accounts\""))));
            }
        }
    }

    @Test
    void sharedNavigationHighlightsCatalogAndKeepsLogoutCsrfProtected() throws Exception {
        var actor = principal(UserRole.LIBRARIAN);
        mockMvc.perform(get("/catalog").with(user(actor))).andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*href=\"/catalog\"\\s+aria-current=\"page\".*")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mockMvc.perform(get("/staff/loans/checkout").with(user(actor))).andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*href=\"/staff/loans/checkout\"\\s+aria-current=\"page\".*")));
        mockMvc.perform(post("/logout").with(user(actor))).andExpect(status().isForbidden());
        mockMvc.perform(post("/logout").with(user(actor)).with(csrf())).andExpect(redirectedUrl("/login?logout=true"));
    }

    @Test
    void passwordChangeRequirementStillBlocksHomeData() throws Exception {
        var account = accounts.createUser("home_" + token(), "home-test-password-123", "初次登录", UserRole.READER);
        mockMvc.perform(get("/").with(user(new AppUserPrincipal(account))))
                .andExpect(redirectedUrl("/account/password"));
    }

    private AppUserPrincipal principal(UserRole role) {
        var account = accounts.createUser("home_" + token(), "home-test-password-123", "首页测试", role);
        account.markPasswordChanged();
        return new AppUserPrincipal(users.saveAndFlush(account));
    }

    private static String token() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
