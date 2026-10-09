package com.example.library.account;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {

    public static final int MIN_CODE_POINTS = 12;
    public static final int MAX_CODE_POINTS = 64;
    public static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null) {
            throw new IllegalArgumentException("密码不能为空");
        }
        int codePoints = password.codePointCount(0, password.length());
        if (codePoints < MIN_CODE_POINTS || codePoints > MAX_CODE_POINTS) {
            throw new IllegalArgumentException("密码长度须为 12—64 个字符");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("密码 UTF-8 编码长度不能超过 72 字节");
        }
    }
}
