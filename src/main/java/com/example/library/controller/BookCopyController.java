package com.example.library.controller;

import com.example.library.catalog.Book;
import com.example.library.catalog.BookCopy;
import com.example.library.catalog.BookCopyService;
import com.example.library.catalog.BookService;
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
public class BookCopyController {

    private final BookCopyService copies;
    private final BookService books;
    private final CurrentIdentityService currentIdentity;

    public BookCopyController(BookCopyService copies, BookService books, CurrentIdentityService currentIdentity) {
        this.copies = copies;
        this.books = books;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/staff/copies")
    public String copies(@RequestParam(required = false) String q, Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("query", q == null ? "" : q.trim());
        model.addAttribute("books", books.listActiveBooksForCatalog());
        try {
            List<BookCopy> results = copies.search(actor.role(), q);
            model.addAttribute("copies", results);
            model.addAttribute("copyInventory", copies.inventoryByBookIds(
                    results.stream().map(BookCopy::getBookId).distinct().toList()));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("copies", List.of());
            model.addAttribute("copyInventory", java.util.Map.of());
            model.addAttribute("error", exception.getMessage());
        }
        return "catalog/copies";
    }

    @PostMapping("/staff/copies")
    public String register(@RequestParam Long bookId, @RequestParam String barcode,
                           @RequestParam(required = false) String shelfLocation, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        try {
            copies.register(actor.role(), bookId, barcode, shelfLocation);
            redirect.addFlashAttribute("notice", "副本已登记，初始状态为可借。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("error", "馆藏条码已被使用，或书目已经发生变化，请核对后重试。");
        }
        return "redirect:/staff/copies";
    }

    @PostMapping("/staff/copies/{id}/withdraw")
    public String withdraw(@PathVariable Long id, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        try {
            copies.withdraw(actor.role(), id);
            redirect.addFlashAttribute("notice", "副本已注销，历史记录仍会保留。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/staff/copies";
    }
}
