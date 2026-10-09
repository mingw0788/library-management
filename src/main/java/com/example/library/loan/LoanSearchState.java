package com.example.library.loan;

import java.util.Locale;

public enum LoanSearchState {
    ALL,
    CURRENT,
    RETURNED;

    public static LoanSearchState parse(String value) {
        if (value == null || value.isBlank()) return CURRENT;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("借阅状态筛选无效");
        }
    }
}
