package com.example.library.web;

import com.example.library.account.*;
import com.example.library.security.AppUserPrincipal;
import com.example.library.catalog.BookService;
import com.example.library.catalog.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "library.reservations.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NavigationFeatureDisabledTest {
    @Autowired private MockMvc mvc;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private BookService books;
    @Autowired private CategoryService categories;

    @Test
    void readerHomeKeepsBorrowingRemindersWithoutReservationCardWhenDisabled() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var account = accounts.createReader(UserRole.ADMIN, "nav_remind_" + suffix, "navigation-test-123",
                "提醒读者", "NAV-" + suffix, null);
        account.markPasswordChanged();
        var actor = new AppUserPrincipal(users.saveAndFlush(account));
        var result = mvc.perform(get("/").with(user(actor))).andExpect(status().isOk())
                .andExpect(content().string(containsString("近期应还")))
                .andExpect(content().string(not(containsString("id=\"ready-title\"")))).andReturn();
        var reminders = (com.example.library.dashboard.ReaderReminders) result.getModelAndView().getModel().get("readerReminders");
        org.junit.jupiter.api.Assertions.assertFalse(reminders.reservationsEnabled());
        org.junit.jupiter.api.Assertions.assertTrue(reminders.ready().isEmpty());
    }

    @Test
    void disabledReservationsHaveNoBookDetailFormAndRejectNewRoute() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var category = categories.create(UserRole.ADMIN, "预约关闭" + suffix);
        var book = books.create(UserRole.ADMIN, "预约关闭书目" + suffix, "作者", null, null, null, category.getId(), null);
        var account = accounts.createUser("nav_" + suffix, "navigation-test-123", "读者", UserRole.READER);
        account.markPasswordChanged();
        var actor = new AppUserPrincipal(users.saveAndFlush(account));
        mvc.perform(get("/catalog/{id}", book.getId()).with(user(actor))).andExpect(status().isOk())
                .andExpect(model().attributeDoesNotExist("reservationOffer"))
                .andExpect(content().string(not(containsString("id=\"reservation\""))));
        mvc.perform(post("/reader/reservations/books/{id}", book.getId()).with(user(actor)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void disabledReservationsHaveNoNavigationOrHomeServiceLinks() throws Exception {
        for (UserRole role : UserRole.values()) {
            String name = "nav_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            var account = accounts.createUser(name, "navigation-test-123", "导航测试", role);
            account.markPasswordChanged();
            var actor = new AppUserPrincipal(users.saveAndFlush(account));
            for (String route : new String[]{"/", "/catalog"}) {
                mvc.perform(get(route).with(user(actor))).andExpect(status().isOk())
                        .andExpect(content().string(not(containsString("href=\"/reader/reservations\""))))
                        .andExpect(content().string(not(containsString("href=\"/staff/reservations\""))));
            }
        }
    }
}
