package com.example.library.web;

import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Set;

/** Rebuilds only a local catalogue destination from bounded browse parameters. */
public final class CatalogReturn {
    private static final Set<String> SORTS = Set.of("titleAsc", "titleDesc", "authorAsc", "authorDesc", "dateAsc", "dateDesc");

    private CatalogReturn() {}

    public static String target(Map<String, String> parameters) {
        try {
            var uri = UriComponentsBuilder.fromPath("/catalog");
            String query = parameters.getOrDefault("q", "").trim();
            if (query.codePointCount(0, query.length()) > 100 || query.codePoints().anyMatch(Character::isISOControl)) {
                return "/catalog";
            }
            if (!query.isEmpty()) uri.queryParam("q", query);
            String category = parameters.get("categoryId");
            if (category != null && !category.isBlank()) {
                long id = Long.parseLong(category);
                if (id < 1) return "/catalog";
                uri.queryParam("categoryId", id);
            }
            String sort = parameters.get("sort");
            if (sort != null && !sort.isBlank()) {
                if (!SORTS.contains(sort)) return "/catalog";
                uri.queryParam("sort", sort);
            }
            String size = parameters.get("size");
            if (size != null && !size.isBlank()) {
                int value = Integer.parseInt(size);
                if (value < 1 || value > 100) return "/catalog";
                uri.queryParam("size", value);
            }
            String page = parameters.get("page");
            if (page != null && !page.isBlank()) {
                int value = Integer.parseInt(page);
                if (value < 0 || value > 100_000) return "/catalog";
                uri.queryParam("page", value);
            }
            return uri.build().encode().toUriString();
        } catch (IllegalArgumentException exception) {
            return "/catalog";
        }
    }
}
