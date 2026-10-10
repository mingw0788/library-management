package com.example.library.web;

import com.example.library.account.*;
import com.example.library.catalog.*;
import com.example.library.security.AppUserPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ManagementPaginationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ManagementQueryService lists;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private CategoryService categories;
    @Autowired private BookService books;
    @Autowired private BookCopyService copies;

    private record Fixtures(String marker, AppUserPrincipal actor, List<AppUser> readers,
                            List<Book> books, List<BookCopy> copies) {}

    private Fixtures fixtures(int count) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String marker = "分页&中文" + suffix;
        AppUser staff = accounts.createUser("paging_staff_" + suffix, "paging-test-pass-123", "分页馆员", UserRole.LIBRARIAN);
        staff.markPasswordChanged();
        users.saveAndFlush(staff);
        Category category = categories.create(UserRole.LIBRARIAN, marker);
        List<AppUser> readers = new ArrayList<>();
        List<Book> titles = new ArrayList<>();
        List<BookCopy> inventory = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            AppUser reader = accounts.createReader(UserRole.LIBRARIAN, "paging_reader_" + suffix + "_" + index,
                    "paging-test-pass-123", marker + index, marker + String.format("%03d", index), null);
            reader.markPasswordChanged();
            readers.add(users.saveAndFlush(reader));
            Book title = books.create(UserRole.LIBRARIAN, marker, "分页作者", null, null, null, category.getId(), null);
            titles.add(title);
            inventory.add(copies.register(UserRole.LIBRARIAN, title.getId(), marker + String.format("%03d", index), "A区"));
        }
        return new Fixtures(marker, new AppUserPrincipal(staff), readers, titles, inventory);
    }

    @Test
    void allThreeListsPageInStableOrderAndCountFilteredHistoricalStates() {
        Fixtures data = fixtures(21);
        Set<Long> bookIds = new HashSet<>();
        Set<Long> copyIds = new HashSet<>();
        Set<Long> readerIds = new HashSet<>();
        for (int index = 0; index < 3; index++) {
            var search = new ManagementSearch(data.marker, "ALL", index);
            var titles = lists.books(UserRole.LIBRARIAN, search);
            var inventory = lists.copies(UserRole.LIBRARIAN, search);
            var readers = lists.readers(UserRole.LIBRARIAN, search);
            int expected = index < 2 ? 10 : 1;
            assertEquals(expected, titles.getNumberOfElements());
            assertEquals(expected, inventory.getNumberOfElements());
            assertEquals(expected, readers.getNumberOfElements());
            assertEquals(21, titles.getTotalElements());
            assertEquals(21, inventory.getTotalElements());
            assertEquals(21, readers.getTotalElements());
            titles.forEach(row -> assertTrue(bookIds.add(row.getId()), "书名相同也不得跨页重复"));
            inventory.forEach(row -> assertTrue(copyIds.add(row.getId())));
            readers.forEach(row -> assertTrue(readerIds.add(row.userId())));
        }
        assertEquals(21, bookIds.size());
        assertEquals(21, copyIds.size());
        assertEquals(21, readerIds.size());
        copies.withdraw(UserRole.LIBRARIAN, data.copies.getLast().getId());
        books.archive(UserRole.LIBRARIAN, data.books.getLast().getId());
        accounts.disableReader(UserRole.LIBRARIAN, data.readers.getLast().getId());
        assertEquals(1, lists.books(UserRole.ADMIN, new ManagementSearch(data.marker, "ARCHIVED", 0)).getTotalElements());
        assertEquals(1, lists.copies(UserRole.ADMIN, new ManagementSearch(data.marker, "WITHDRAWN", 0)).getTotalElements());
        assertEquals(1, lists.readers(UserRole.ADMIN, new ManagementSearch(data.marker, "DISABLED", 0)).getTotalElements());
        assertEquals(20, lists.readers(UserRole.ADMIN, new ManagementSearch(data.marker, "ACTIVE", 0)).getTotalElements());
        assertEquals(1, lists.readers(UserRole.ADMIN, new ManagementSearch(data.readers.getFirst().getLoginName(), "ALL", 0)).getTotalElements());
        assertEquals(2, lists.books(UserRole.ADMIN, new ManagementSearch(data.marker, "ALL", 999)).getNumber());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> lists.readers(UserRole.READER, new ManagementSearch(data.marker, "ALL", 0)));
    }

    @Test
    void navigationAndFailedEditRetainEncodedFiltersAndSecondPage() throws Exception {
        Fixtures data = fixtures(12);
        for (String path : List.of("/staff/readers", "/staff/books", "/staff/copies")) {
            String state = path.endsWith("copies") ? "AVAILABLE" : "ACTIVE";
            var result = mvc.perform(get(path).with(user(data.actor)).param("q", data.marker)
                            .param("status", state).param("page", "1"))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("上一页")))
                    .andExpect(content().string(containsString("name=\"listPage\" value=\"1\"")))
                    .andExpect(content().string(containsString("name=\"_csrf\""))).andReturn();
            Page<?> page = (Page<?>) result.getModelAndView().getModel().get("resultPage");
            assertEquals(12, page.getTotalElements());
            assertEquals(2, page.getContent().size());
        }
        AppUser last = data.readers.getLast();
        String expected = new ManagementReturn(data.marker, "ACTIVE", "1")
                .target("/staff/readers", ManagementQueryService.READER_STATES);
        var failed = mvc.perform(post("/staff/readers/{id}/update", last.getId()).with(user(data.actor)).with(csrf())
                        .param("displayName", "").param("listQuery", data.marker)
                        .param("listStatus", "ACTIVE").param("listPage", "1").param("returnUrl", "https://example.com"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(expected))
                .andExpect(flash().attributeExists("fieldErrors")).andReturn();
        mvc.perform(get(java.net.URI.create(expected)).with(user(data.actor)).flashAttrs(failed.getFlashMap()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("READER_UPDATE_" + last.getId())))
                .andExpect(content().string(containsString("name=\"listPage\" value=\"1\"")));
        mvc.perform(post("/staff/copies/{id}/withdraw", data.copies.getLast().getId()).with(user(data.actor)).with(csrf())
                        .param("listQuery", data.marker).param("listStatus", "AVAILABLE").param("listPage", "1"))
                .andExpect(redirectedUrl(new ManagementReturn(data.marker, "AVAILABLE", "1")
                        .target("/staff/copies", ManagementQueryService.COPY_STATES)));
        mvc.perform(post("/staff/books/{id}/archive", data.books.getLast().getId()).with(user(data.actor)).with(csrf())
                        .param("listQuery", data.marker).param("listStatus", "ACTIVE").param("listPage", "1"))
                .andExpect(redirectedUrl(new ManagementReturn(data.marker, "ACTIVE", "1")
                        .target("/staff/books", ManagementQueryService.BOOK_STATES)));
    }

    @Test
    void invalidInputsEmptyResultsAndRoleBoundariesAreSafe() throws Exception {
        Fixtures data = fixtures(1);
        var reader = new AppUserPrincipal(data.readers.getFirst());
        for (String path : List.of("/staff/readers", "/staff/books", "/staff/copies")) {
            mvc.perform(get(path)).andExpect(status().is3xxRedirection());
            mvc.perform(get(path).with(user(reader))).andExpect(status().isForbidden());
            for (var bad : List.of(Map.of("page", "-1"), Map.of("page", "99999999999999"),
                    Map.of("status", "invalid"), Map.of("q", "a".repeat(101)), Map.of("q", "bad\nquery"))) {
                var request = get(path).with(user(data.actor));
                bad.forEach(request::param);
                mvc.perform(request).andExpect(status().isOk()).andExpect(model().attributeExists("error"));
            }
            var result = mvc.perform(get(path).with(user(data.actor)).param("q", "不存在%_" + data.marker).param("page", "100000"))
                    .andExpect(status().isOk()).andReturn();
            Page<?> page = (Page<?>) result.getModelAndView().getModel().get("resultPage");
            assertEquals(0, page.getTotalElements());
            assertEquals(0, page.getNumber());
        }
        assertEquals("/staff/readers", new ManagementReturn("", "bad", "-1")
                .target("/staff/readers", ManagementQueryService.READER_STATES));
    }

    @Test
    void pagedCopyInventoryStillCountsAllCopiesOfTheBook() throws Exception {
        Fixtures data = fixtures(1);
        for (int index = 1; index < 12; index++) {
            copies.register(UserRole.LIBRARIAN, data.books.getFirst().getId(), data.marker + "extra" + index, "B区");
        }
        var result = mvc.perform(get("/staff/copies").with(user(data.actor)).param("q", data.marker))
                .andExpect(status().isOk()).andReturn();
        Page<?> page = (Page<?>) result.getModelAndView().getModel().get("resultPage");
        assertEquals(10, page.getNumberOfElements());
        assertEquals(12, page.getTotalElements());
        @SuppressWarnings("unchecked")
        Map<Long, BookCopyInventory> inventory = (Map<Long, BookCopyInventory>) result.getModelAndView().getModel().get("copyInventory");
        assertEquals(12, inventory.get(data.books.getFirst().getId()).available());
    }
}
