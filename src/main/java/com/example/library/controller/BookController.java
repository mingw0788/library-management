package com.example.library.controller;

import com.example.library.account.UserRole;
import com.example.library.catalog.Book;
import com.example.library.catalog.BookService;
import com.example.library.catalog.BookCopyService;
import com.example.library.catalog.Category;
import com.example.library.catalog.CategoryService;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Controller
public class BookController {

    private final BookService books;
    private final BookCopyService copies;
    private final CategoryService categories;
    private final CurrentIdentityService currentIdentity;
    private final ManagementQueryService lists;

    public BookController(BookService books, BookCopyService copies, CategoryService categories,
                          CurrentIdentityService currentIdentity, ManagementQueryService lists) {
        this.books = books;
        this.copies = copies;
        this.categories = categories;
        this.currentIdentity = currentIdentity;
        this.lists = lists;
    }

    @GetMapping("/staff/books")
    public String books(@RequestParam(required = false) String q,
                         @RequestParam(required = false) String status,
                         @RequestParam(defaultValue = "0") String page, Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("categories", categories.search(actor.role(), null));
        try {
            ManagementSearch search = ManagementSearch.parse(q, status, page, ManagementQueryService.BOOK_STATES);
            Page<Book> result = lists.books(actor.role(), search);
            ManagementListModel.apply(model, search, result, "/staff/books", ManagementQueryService.BOOK_STATES);
            List<Book> results = result.getContent();
            model.addAttribute("books", results);
            model.addAttribute("copyInventory", copies.inventoryByBookIds(
                    results.stream().map(Book::getId).toList()));
        } catch (IllegalArgumentException exception) {
            ManagementListModel.apply(model, new ManagementSearch("", "ALL", 0), Page.empty(),
                    "/staff/books", ManagementQueryService.BOOK_STATES);
            model.addAttribute("books", List.of());
            model.addAttribute("copyInventory", java.util.Map.of());
            model.addAttribute("error", exception.getMessage());
        }
        return "catalog/books";
    }

    @PostMapping("/staff/books")
    public String create(@RequestParam(required = false) String title,
                         @RequestParam(required = false) String author,
                         @RequestParam(required = false) String publisher,
                         @RequestParam(required = false) String publishedDate,
                         @RequestParam(required = false) String isbn,
                         @RequestParam(required = false) Long categoryId,
                         @RequestParam(required = false) String description,
                         @ModelAttribute ManagementReturn listReturn, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return performForm(listReturn.target("/staff/books", ManagementQueryService.BOOK_STATES), "BOOK_CREATE",
                FormFeedback.safeValues("title", title, "author", author, "publisher", publisher,
                        "publishedDate", publishedDate, "isbn", isbn, "categoryId", categoryId,
                        "description", description), redirect,
                () -> books.create(actor.role(), title, author, publisher, parseDate(publishedDate),
                        isbn, categoryId, description),
                "title", "author", "publisher", "publishedDate", "isbn", "categoryId", "description");
    }

    @PostMapping("/staff/books/{id}/update")
    public String update(@PathVariable Long id, @RequestParam(required = false) Long version,
                         @RequestParam(required = false) String title, @RequestParam(required = false) String author,
                         @RequestParam(required = false) String publisher,
                         @RequestParam(required = false) String publishedDate,
                         @RequestParam(required = false) String isbn,
                         @RequestParam(required = false) Long categoryId,
                         @RequestParam(required = false) String description,
                         @ModelAttribute ManagementReturn listReturn, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        String formKey = "BOOK_UPDATE_" + id;
        return performForm(listReturn.target("/staff/books", ManagementQueryService.BOOK_STATES), formKey,
                FormFeedback.safeValues("version", version, "title", title, "author", author,
                        "publisher", publisher, "publishedDate", publishedDate, "isbn", isbn,
                        "categoryId", categoryId, "description", description), redirect,
                () -> {
                    if (version == null) throw new IllegalArgumentException("书目版本无效，请刷新后重试");
                    books.update(actor.role(), id, version, title, author, publisher,
                            parseDate(publishedDate), isbn, categoryId, description);
                },
                "title", "author", "publisher", "publishedDate", "isbn", "categoryId", "description");
    }

    @PostMapping("/staff/books/{id}/archive")
    public String archive(@PathVariable Long id, @ModelAttribute ManagementReturn listReturn, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform(listReturn.target("/staff/books", ManagementQueryService.BOOK_STATES), redirect, () -> books.archive(actor.role(), id));
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

    private static String performForm(String target, String formKey, java.util.Map<String, String> safeValues,
                                      RedirectAttributes redirect, Runnable operation, String... candidateFields) {
        try {
            operation.run();
            redirect.addFlashAttribute("notice", "操作已完成。");
        } catch (ObjectOptimisticLockingFailureException exception) {
            redirect.addFlashAttribute("error", "这条书目已被其他工作人员修改，请刷新后再编辑。");
        } catch (IllegalArgumentException exception) {
            FormFeedback.failure(redirect, formKey, exception.getMessage(), safeValues, candidateFields);
        } catch (DataIntegrityViolationException exception) {
            FormFeedback.failure(redirect, formKey,
                    "书目资料与现有分类或 ISBN 冲突，请核对后重试。", safeValues, candidateFields);
        }
        return "redirect:" + target;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("出版日期格式无效，请使用年-月-日");
        }
    }
}
