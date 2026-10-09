package com.example.library.account;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AppUser createUser(String loginName, String rawPassword, String displayName, UserRole role) {
        String normalizedLoginName = LoginNameNormalizer.normalize(loginName);
        PasswordPolicy.validate(rawPassword);
        String normalizedDisplayName = normalizeDisplayName(displayName);
        if (role == null) {
            throw new IllegalArgumentException("账号角色不能为空");
        }
        AppUser user = new AppUser(normalizedLoginName, passwordEncoder.encode(rawPassword), normalizedDisplayName, role);
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<AppUser> findByLoginName(String loginName) {
        return users.findByLoginName(LoginNameNormalizer.normalize(loginName));
    }

    @Transactional
    public void changeOwnPassword(String loginName, String currentPassword, String newPassword) {
        AppUser user = users.findByLoginName(LoginNameNormalizer.normalize(loginName))
                .orElseThrow(() -> new IllegalArgumentException("当前账号不存在"));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        PasswordPolicy.validate(newPassword);
        user.completePasswordChange(passwordEncoder.encode(newPassword));
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        String normalized = value.trim();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints < 1 || codePoints > 100) {
            throw new IllegalArgumentException("姓名长度须为 1—100 个字符");
        }
        return normalized;
    }
}
