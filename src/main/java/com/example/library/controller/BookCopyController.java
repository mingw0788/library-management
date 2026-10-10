package com.example.library.controller;

import com.example.library.catalog.Book;
import com.example.library.catalog.BookCopy;
import com.example.library.catalog.BookCopyService;
import com.example.library.catalog.BookService;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import com.example.library.web.FormFeedback;
import com.example.library.web.ManagementSearch;
import com.example.library.web.ManagementReturn;
import com.example.library.web.ManagementListModel;
import com.example.library.catalog.ManagementQueryService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.ModelAttribute;
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
    private final ManagementQueryService lists;

    public BookCopyController(BookCopyService copies, BookService books, CurrentIdentityService currentIdentity, ManagementQueryService lists) {
        this.copies = copies;
        this.books = books;
        this.currentIdentity = currentIdentity;
        this.lists = lists;
    }

    @GetMapping("/staff/copies")
    public String copies(@RequestParam(required = false) String q,
                         @RequestParam(required = false) String status,
                         @RequestParam(defaultValue = "0") String page, Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("books", books.listActiveBooksForCatalog());
        try {
            ManagementSearch search = ManagementSearch.parse(q, status, page, ManagementQueryService.COPY_STATES);
            Page<BookCopy> result = lists.copies(actor.role(), search);
            ManagementListModel.apply(model, search, result, "/staff/copies", ManagementQueryService.COPY_STATES);
            List<BookCopy> results = result.getContent();
            model.addAttribute("copies", results);
            model.addAttribute("copyInventory", copies.inventoryByBookIds(
                    results.stream().map(BookCopy::getBookId).distinct().toList()));
        } catch (IllegalArgumentException exception) {
            ManagementListModel.apply(model, new ManagementSearch("", "ALL", 0), Page.empty(),
                    "/staff/copies", ManagementQueryService.COPY_STATES);
            model.addAttribute("copies", List.of());
            model.addAttribute("copyInventory", java.util.Map.of());
            model.addAttribute("error", exception.getMessage());
        }
        return "catalog/copies";
    }

    @PostMapping("/staff/copies")
    public String register(@RequestParam(required = false) Long bookId,
                           @RequestParam(required = false) String barcode,
                           @RequestParam(required = false) String shelfLocation, @ModelAttribute ManagementReturn listReturn, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return performForm(listReturn.target("/staff/copies", ManagementQueryService.COPY_STATES), "COPY_REGISTER", FormFeedback.safeValues(
                "bookId", bookId, "barcode", barcode, "shelfLocation", shelfLocation), redirect,
                () -> copies.register(actor.role(), bookId, barcode, shelfLocation), "bookId", "barcode", "shelfLocation");
    }

    @PostMapping("/staff/copies/{id}/withdraw")
    public String withdraw(@PathVariable Long id, @ModelAttribute ManagementReturn listReturn, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        try {
            copies.withdraw(actor.role(), id);
            redirect.addFlashAttribute("notice", "副本已注销，历史记录仍会保留。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:" + listReturn.target("/staff/copies", ManagementQueryService.COPY_STATES);
    }

    private static String performForm(String target, String formKey, java.util.Map<String, String> safeValues,
                                      RedirectAttributes redirect, Runnable operation, String... candidateFields) {
        try {
            operation.run();
            redirect.addFlashAttribute("notice", "副本已登记，初始状态为可借。");
        } catch (IllegalArgumentException exception) {
            FormFeedback.failure(redirect, formKey, exception.getMessage(), safeValues, candidateFields);
        } catch (DataIntegrityViolationException exception) {
            FormFeedback.failure(redirect, formKey,
                    "馆藏条码已被使用，或书目已经发生变化，请核对后重试。", safeValues, candidateFields);
        }
        return "redirect:" + target;
    }
}
