package com.example.library.controller;

import com.example.library.catalog.CatalogBookDetail;
import com.example.library.catalog.CatalogSearchResult;
import com.example.library.catalog.CatalogService;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class CatalogController {

    private final CatalogService catalog;
    private final CurrentIdentityService currentIdentity;

    public CatalogController(CatalogService catalog, CurrentIdentityService currentIdentity) {
        this.catalog = catalog;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/catalog")
    public String search(@RequestParam(required = false) String q,
                         @RequestParam(required = false) Long categoryId,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size,
                         @RequestParam(defaultValue = "titleAsc") String sort,
                         Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("query", q == null ? "" : q.trim());
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("sort", sort);
        model.addAttribute("categories", catalog.categories());
        try {
            CatalogSearchResult result = catalog.search(q, categoryId, page, size, sort);
            model.addAttribute("booksPage", result.books());
            model.addAttribute("inventory", result.inventory());
            model.addAttribute("categoryNames", result.categoryNames());
            model.addAttribute("categoryId", result.categoryId());
            model.addAttribute("sort", result.sort());
            model.addAttribute("pageSize", result.books().getSize());
            addPageWindow(model, result.books());
        } catch (IllegalArgumentException exception) {
            Page<?> empty = Page.empty(PageRequest.of(0, Math.max(1, Math.min(size, 100))));
            model.addAttribute("booksPage", empty);
            model.addAttribute("inventory", Map.of());
            model.addAttribute("pageSize", Math.max(1, Math.min(size, 100)));
            addPageWindow(model, empty);
            model.addAttribute("error", exception.getMessage());
        }
        return "catalog/search";
    }

    @GetMapping("/catalog/{id}")
    public String detail(@PathVariable Long id, Model model, HttpServletResponse response) throws IOException {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        try {
            CatalogBookDetail detail = catalog.detail(id);
            model.addAttribute("detail", detail);
            return "catalog/detail";
        } catch (IllegalArgumentException exception) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("message", "没有找到这本有效书目。它可能不存在或已归档。");
            return "catalog/not-found";
        }
    }

    private static void addPageWindow(Model model, Page<?> page) {
        int totalPages = page.getTotalPages();
        int start = Math.max(0, page.getNumber() - 2);
        int end = Math.min(totalPages - 1, page.getNumber() + 2);
        List<Integer> pageNumbers = new ArrayList<>();
        for (int number = start; number <= end; number++) pageNumbers.add(number);
        model.addAttribute("pageNumbers", pageNumbers);
        model.addAttribute("showFirstPage", start > 0);
        model.addAttribute("showLastPage", end < totalPages - 1);
    }
}
