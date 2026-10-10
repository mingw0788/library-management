package com.example.library.web;

import java.util.Locale;
import java.util.Set;

public record ManagementSearch(String query, String status, int page) {
    public static final int PAGE_SIZE = 10;

    public static ManagementSearch parse(String query, String status, String page, Set<String> statuses) {
        String q = query == null ? "" : query.trim();
        if (q.codePointCount(0, q.length()) > 100 || q.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("查询词最多 100 个字符，不能包含控制字符。");
        }
        String state = status == null || status.isBlank() ? "ALL" : status.trim().toUpperCase(Locale.ROOT);
        if (!state.equals("ALL") && !statuses.contains(state)) throw new IllegalArgumentException("状态筛选无效。");
        int index;
        try { index = page == null || page.isBlank() ? 0 : Integer.parseInt(page); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("页码无效。"); }
        if (index < 0 || index > 100_000) throw new IllegalArgumentException("页码须在 0—100000 之间。");
        return new ManagementSearch(q, state, index);
    }
}
