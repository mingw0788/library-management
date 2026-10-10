package com.example.library.web;

import com.example.library.loan.LoanSearchState;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.DateTimeException;
import java.util.Map;

/** Rebuild only the authenticated reader's fixed list route. */
public final class LoanListReturn {
    private LoanListReturn() { }

    public static String target(Map<String, String> parameters) {
        if (parameters.keySet().stream().noneMatch(key -> key.startsWith("list"))) return "/reader/loans";
        try {
            String state = LoanSearchState.parse(parameters.get("listState")).name();
            int page = Integer.parseInt(parameters.getOrDefault("listPage", "0"));
            if (page < 0 || page > 100_000) return "/reader/loans";
            var builder = UriComponentsBuilder.fromPath("/reader/loans")
                    .queryParam("state", state).queryParam("page", page);
            for (String field : new String[]{"fromDate", "toDate"}) {
                String value = parameters.get("list" + Character.toUpperCase(field.charAt(0)) + field.substring(1));
                if (value != null && !value.isBlank()) builder.queryParam(field, LocalDate.parse(value));
            }
            return builder.build().encode().toUriString();
        } catch (IllegalArgumentException | DateTimeException exception) {
            return "/reader/loans";
        }
    }
}
