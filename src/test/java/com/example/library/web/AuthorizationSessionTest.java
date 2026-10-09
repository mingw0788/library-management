package com.example.library.web;

import com.example.library.account.AccountService;
import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.ReaderProfileRepository;
import com.example.library.account.UserRole;
import com.example.library.account.UserStatus;
import com.example.library.security.AppUserPrincipal;
import com.example.library.security.CurrentIdentityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationSessionTest {

    private static final String PASSWORD = "authorization-pass-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private ReaderProfileRepository readerProfiles;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CurrentIdentityService currentIdentity;

    private final List<Long> createdUserIds = new ArrayList<>();
    private TestUser admin;
    private TestUser librarian;
    private TestUser reader;

    @BeforeEach
    void createRoleFixtures() {
        admin = createUser(UserRole.ADMIN);
        librarian = createUser(UserRole.LIBRARIAN);
        reader = createReader();
    }

    @AfterEach
    void removeRoleFixtures() {
        for (Long id : createdUserIds) {
            readerProfiles.findByUserId(id).ifPresent(readerProfiles::delete);
            users.deleteById(id);
        }
        readerProfiles.flush();
        createdUserIds.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void eachRoleCanOnlyOpenItsAssignedWorkspace() throws Exception {
        MockHttpSession adminSession = login(admin);
        mockMvc.perform(get("/admin").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/admin"));
        mockMvc.perform(get("/staff").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/staff"));
        mockMvc.perform(get("/reader").session(adminSession)).andExpect(status().isForbidden());

        MockHttpSession librarianSession = login(librarian);
        mockMvc.perform(get("/staff").session(librarianSession))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/staff"));
        mockMvc.perform(get("/admin").session(librarianSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/reader").session(librarianSession)).andExpect(status().isForbidden());

        MockHttpSession readerSession = login(reader);
        mockMvc.perform(get("/reader").session(readerSession))
                .andExpect(status().isOk())
                .andExpect(view().name("workspace/reader"))
                .andExpect(content().string(containsString("我的借阅记录")));
        mockMvc.perform(get("/admin").session(readerSession)).andExpect(status().isForbidden());
        mockMvc.perform(get("/staff").session(readerSession)).andExpect(status().isForbidden());
    }

    @Test
    void anonymousDirectRequestsRedirectAndRequestRoleParametersCannotGrantAccess() throws Exception {
        for (String path : List.of("/admin", "/staff", "/reader")) {
            mockMvc.perform(get(path))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login"));
        }

        MockHttpSession readerSession = login(reader);
        mockMvc.perform(get("/admin?role=ADMIN").session(readerSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/staff?role=LIBRARIAN").session(readerSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void currentIdentityProvidesReaderOwnershipAndRejectsOtherAccounts() {
        try {
            var readerUser = users.findById(reader.id()).orElseThrow();
            var readerAuthentication = new UsernamePasswordAuthenticationToken(
                    new AppUserPrincipal(readerUser), "", new AppUserPrincipal(readerUser).getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(readerAuthentication);

            assertEquals(reader.id(), currentIdentity.requireReaderUserId());
            currentIdentity.requireOwnReaderUserId(reader.id());
            assertThrows(AccessDeniedException.class, () -> currentIdentity.requireOwnReaderUserId(admin.id()));

            var staffUser = users.findById(librarian.id()).orElseThrow();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new AppUserPrincipal(staffUser), "", new AppUserPrincipal(staffUser).getAuthorities()));
            assertThrows(AccessDeniedException.class, currentIdentity::requireReaderUserId);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void disablingAnAccountOrChangingItsCredentialVersionInvalidatesOldReadAndWriteSessions() throws Exception {
        for (UserRole role : List.of(UserRole.ADMIN, UserRole.LIBRARIAN, UserRole.READER)) {
            for (boolean disable : List.of(false, true)) {
                for (boolean writeRequest : List.of(false, true)) {
                    TestUser sessionUser = createUser(role);
                    MockHttpSession session = login(sessionUser);

                    AppUser changed = users.findById(sessionUser.id()).orElseThrow();
                    if (disable) {
                        changed.changeStatus(UserStatus.DISABLED);
                    } else {
                        changed.replacePasswordHash(passwordEncoder.encode("replacement-password-456"));
                    }
                    users.saveAndFlush(changed);

                    if (writeRequest) {
                        mockMvc.perform(post("/account/profile").with(csrf()).session(session)
                                        .param("displayName", "不应写入"))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(redirectedUrl("/login?expired=true"));
                    } else {
                        mockMvc.perform(get("/").session(session))
                                .andExpect(status().is3xxRedirection())
                                .andExpect(redirectedUrl("/login?expired=true"));
                    }
                    assertTrue(session.isInvalid());
                    assertEquals("权限测试", users.findById(sessionUser.id()).orElseThrow().getDisplayName(),
                            "过期会话不得继续读取或修改账号");
                }
            }
        }
    }

    private TestUser createUser(UserRole role) {
        String loginName = "access_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AppUser user = accounts.createUser(loginName, PASSWORD, "权限测试", role);
        user.markPasswordChanged();
        AppUser saved = users.saveAndFlush(user);
        createdUserIds.add(saved.getId());
        return new TestUser(saved.getId(), loginName, role);
    }

    private TestUser createReader() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String loginName = "access_reader_" + suffix;
        AppUser user = accounts.createReader(UserRole.ADMIN, loginName, PASSWORD,
                "权限测试读者", "ACCESS-" + suffix, null);
        user.markPasswordChanged();
        AppUser saved = users.saveAndFlush(user);
        createdUserIds.add(saved.getId());
        return new TestUser(saved.getId(), loginName, UserRole.READER);
    }

    private MockHttpSession login(TestUser user) throws Exception {
        var result = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "  " + user.loginName().toUpperCase(Locale.ROOT) + "  ")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private record TestUser(Long id, String loginName, UserRole role) {
    }
}
