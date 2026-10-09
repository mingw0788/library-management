package com.example.library.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final boolean enabled;
    private final String loginName;
    private final String password;
    private final String displayName;
    private final AppUserRepository users;
    private final AccountService accounts;

    public AdminBootstrapRunner(
            @Value("${library.bootstrap.admin.enabled:false}") boolean enabled,
            @Value("${library.bootstrap.admin.username:}") String loginName,
            @Value("${library.bootstrap.admin.password:}") String password,
            @Value("${library.bootstrap.admin.display-name:}") String displayName,
            AppUserRepository users,
            AccountService accounts) {
        this.enabled = enabled;
        this.loginName = loginName;
        this.password = password;
        this.displayName = displayName;
        this.users = users;
        this.accounts = accounts;
    }

    @Override
    public void run(ApplicationArguments args) {
        initializeIfNecessary();
    }

    void initializeIfNecessary() {
        if (!enabled) {
            return;
        }
        if (users.existsByRole(UserRole.ADMIN)) {
            log.info("管理员初始化已跳过：系统中已有管理员账号。");
            return;
        }
        if (loginName.isBlank() || password.isBlank() || displayName.isBlank()) {
            throw new IllegalStateException("已启用首次管理员初始化，但缺少凭据。请设置 LIBRARY_INITIAL_ADMIN_USERNAME、LIBRARY_INITIAL_ADMIN_PASSWORD 和 LIBRARY_INITIAL_ADMIN_DISPLAY_NAME。");
        }
        accounts.createUser(loginName, password, displayName, UserRole.ADMIN);
        log.info("首次管理员账号已创建。请使用受控方式提供的凭据登录并立即修改密码。");
    }
}
