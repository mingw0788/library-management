package com.example.library.account;

import java.util.Locale;
import java.util.regex.Pattern;

public final class LoginNameNormalizer {

    private static final Pattern VALID_LOGIN_NAME = Pattern.compile("[a-z0-9_]{3,50}");

    private LoginNameNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("登录名不能为空");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!VALID_LOGIN_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("登录名须为 3—50 位 ASCII 字母、数字或下划线");
        }
        return normalized;
    }
}
