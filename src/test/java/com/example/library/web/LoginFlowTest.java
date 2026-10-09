package com.example.library.web;

import com.example.library.account.AccountService;
import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.UserRole;
import com.example.library.account.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class LoginFlowTest {

    private static final String INITIAL_PASSWORD = "starter-pass-123";
    private static final String CHANGED_PASSWORD = "updated-pass-456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String loginName;
    private Long userId;

    @BeforeEach
    void createActiveUser() {
        loginName = "login_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        AppUser user = accounts.createUser(loginName, INITIAL_PASSWORD, "登录测试", UserRole.READER);
        userId = user.getId();
    }

    @AfterEach
    void removeTestUser() {
        if (userId != null) {
            users.deleteById(userId);
        }
    }

    @Test
    void successfulLoginRequiresPasswordChangeAndLogoutInvalidatesSession() throws Exception {
        MockHttpSession session = loginSuccessfully();

        mockMvc.perform(get("/").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/password"));
        mockMvc.perform(get("/account/password").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("account/password"))
                .andExpect(content().string(containsString("修改密码")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mockMvc.perform(get("/not-yet-implemented").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/password"));

        mockMvc.perform(post("/logout").session(session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/logout").with(csrf()).session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout=true"));
        assertTrue(session.isInvalid());
        mockMvc.perform(get("/account/password").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void passwordChangeChecksCurrentPasswordThenInvalidatesSessionAndAllowsRelogin() throws Exception {
        MockHttpSession session = loginSuccessfully();
        AppUser before = users.findById(userId).orElseThrow();
        long previousCredentialVersion = before.getCredentialVersion();

        mockMvc.perform(post("/account/password").with(csrf()).session(session)
                        .param("currentPassword", "incorrect-current")
                        .param("newPassword", CHANGED_PASSWORD)
                        .param("confirmPassword", CHANGED_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("当前密码不正确")));
        assertEquals(previousCredentialVersion, users.findById(userId).orElseThrow().getCredentialVersion());

        mockMvc.perform(post("/account/password").with(csrf()).session(session)
                        .param("currentPassword", INITIAL_PASSWORD)
                        .param("newPassword", CHANGED_PASSWORD)
                        .param("confirmPassword", "different-pass-789"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("两次输入的新密码不一致")));

        mockMvc.perform(post("/account/password").with(csrf()).session(session)
                        .param("currentPassword", INITIAL_PASSWORD)
                        .param("newPassword", CHANGED_PASSWORD)
                        .param("confirmPassword", CHANGED_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?changed=true"));

        AppUser changed = users.findById(userId).orElseThrow();
        assertFalse(changed.isMustChangePassword());
        assertEquals(previousCredentialVersion + 1, changed.getCredentialVersion());
        assertTrue(passwordEncoder.matches(CHANGED_PASSWORD, changed.getPasswordHash()));
        assertFalse(passwordEncoder.matches(INITIAL_PASSWORD, changed.getPasswordHash()));
        mockMvc.perform(get("/account/password").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
        assertTrue(session.isInvalid());
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", loginName)
                        .param("password", INITIAL_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true"));

        MockHttpSession newSession = postLogin(CHANGED_PASSWORD, "/");
        mockMvc.perform(get("/").session(newSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("退出登录")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mockMvc.perform(get("/account/password").session(newSession))
                .andExpect(status().isOk())
                .andExpect(view().name("account/password"));
    }

    @Test
    void wrongPasswordAndDisabledUserReceiveTheSameLoginFailure() throws Exception {
        String invalidLocation = postLoginFailure("missing_user_123", "anything-pass-123");
        mockMvc.perform(get(invalidLocation))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("用户名或密码错误，或账号不可用。")));

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", loginName)
                        .param("password", "wrong-pass-123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(invalidLocation));

        AppUser user = users.findById(userId).orElseThrow();
        user.changeStatus(UserStatus.DISABLED);
        users.saveAndFlush(user);
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", loginName)
                        .param("password", INITIAL_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(invalidLocation));
    }

    @Test
    void loginPageAndPasswordWritesRetainCsrfProtection() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));

        MockHttpSession session = loginSuccessfully();
        mockMvc.perform(post("/account/password").session(session)
                        .param("currentPassword", INITIAL_PASSWORD)
                        .param("newPassword", CHANGED_PASSWORD)
                        .param("confirmPassword", CHANGED_PASSWORD))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession loginSuccessfully() throws Exception {
        return postLogin(INITIAL_PASSWORD, "/");
    }

    private MockHttpSession postLogin(String password, String expectedRedirect) throws Exception {
        var result = mockMvc.perform(post("/login").with(csrf())
                        .param("username", "  " + loginName.toUpperCase(java.util.Locale.ROOT) + "  ")
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expectedRedirect))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private String postLoginFailure(String username, String password) throws Exception {
        return mockMvc.perform(post("/login").with(csrf())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andReturn()
                .getResponse().getRedirectedUrl();
    }
}
