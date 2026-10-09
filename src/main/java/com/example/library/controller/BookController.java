package com.example.library.controller;

import com.example.library.account.UserRole;
import com.example.library.catalog.Book;
import com.example.library.catalog.BookService;
import com.example.library.catalog.BookCopyService;
import com.example.library.catalog.Category;
import com.example.library.catalog.CategoryService;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
public class BookController {

    private final BookService books;
    private final BookCopyService copies;
    private final CategoryService categories;
    private final CurrentIdentityService currentIdentity;

    public BookController(BookService books, BookCopyService copies, CategoryService categories,
                          CurrentIdentityService currentIdentity) {
        this.books = books;
        this.copies = copies;
        this.categories = categories;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/staff/books")
    public String books(@RequestParam(required = false) String q, Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("query", q == null ? "" : q.trim());
        model.addAttribute("categories", categories.search(actor.role(), null));
        try {
            List<Book> results = books.search(actor.role(), q);
            model.addAttribute("books", results);
            model.addAttribute("copyInventory", copies.inventoryByBookIds(
                    results.stream().map(Book::getId).toList()));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("books", List.of());
            model.addAttribute("copyInventory", java.util.Map.of());
            model.addAttribute("error", exception.getMessage());
        }
        return "catalog/books";
    }

    @PostMapping("/staff/books")
    public String create(@RequestParam String title, @RequestParam String author,
                         @RequestParam(required = false) String publisher,
                         @RequestParam(required = false) LocalDate publishedDate,
                         @RequestParam(required = false) String isbn,
                         @RequestParam Long categoryId,
                         @RequestParam(required = false) String description,
                         RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/staff/books", redirect, () -> books.create(actor.role(), title, author, publisher,
                publishedDate, isbn, categoryId, description));
    }

    @PostMapping("/staff/books/{id}/update")
    public String update(@PathVariable Long id, @RequestParam long version,
                         @RequestParam String title, @RequestParam String author,
                         @RequestParam(required = false) String publisher,
                         @RequestParam(required = false) LocalDate publishedDate,
                         @RequestParam(required = false) String isbn,
                         @RequestParam Long categoryId,
                         @RequestParam(required = false) String description,
                         RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/staff/books", redirect, () -> books.update(actor.role(), id, version, title, author,
                publisher, publishedDate, isbn, categoryId, description));
    }

    @PostMapping("/staff/books/{id}/archive")
    public String archive(@PathVariable Long id, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/staff/books", redirect, () -> books.archive(actor.role(), id));
    }

    private static String perform(String target, RedirectAttributes redirect, Runnable operation) {
        try {
            operation.run();
            redirect.addFlashAttribute("notice", "操作已完成。");
        } catch (ObjectOptimisticLockingFailureException exception) {
            redirect.addFlashAttribute("error", "这条书目已被其他工作人员修改，请刷新后再编辑。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("error", "书目资料与现有分类或 ISBN 冲突，请核对后重试。");
        }
        return "redirect:" + target;
    }
}
