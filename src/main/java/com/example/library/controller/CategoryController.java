package com.example.library.controller;

import com.example.library.account.UserRole;
import com.example.library.catalog.CategoryService;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class CategoryController {

    private final CategoryService categories;
    private final CurrentIdentityService currentIdentity;

    public CategoryController(CategoryService categories, CurrentIdentityService currentIdentity) {
        this.categories = categories;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/staff/categories")
    public String categories(@RequestParam(required = false) String q, Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("query", q == null ? "" : q.trim());
        try {
            model.addAttribute("categories", categories.search(actor.role(), q));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("categories", List.of());
            model.addAttribute("error", exception.getMessage());
        }
        return "catalog/categories";
    }

    @PostMapping("/staff/categories")
    public String create(@RequestParam String name, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/staff/categories", redirect, () -> categories.create(actor.role(), name));
    }

    @PostMapping("/staff/categories/{id}/rename")
    public String rename(@PathVariable Long id, @RequestParam String name, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/staff/categories", redirect, () -> categories.rename(actor.role(), id, name));
    }

    @PostMapping("/staff/categories/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/staff/categories", redirect, () -> categories.delete(actor.role(), id));
    }

    private static String perform(String target, RedirectAttributes redirect, Runnable operation) {
        try {
            operation.run();
            redirect.addFlashAttribute("notice", "操作已完成。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("error", "分类名称重复，或分类已被书目引用，不能删除。");
        }
        return "redirect:" + target;
    }
}
