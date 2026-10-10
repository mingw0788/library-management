package com.example.library.controller;

import com.example.library.account.AccountService;
import com.example.library.account.UserRole;
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
public class AccountManagementController {

    private final AccountService accounts;
    private final CurrentIdentityService currentIdentity;
    private final ManagementQueryService lists;

    public AccountManagementController(AccountService accounts,
                                       CurrentIdentityService currentIdentity, ManagementQueryService lists) {
        this.accounts = accounts;
        this.currentIdentity = currentIdentity;
        this.lists = lists;
    }

    @GetMapping("/admin/accounts")
    public String staffAccounts(Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("staff", accounts.listStaff(actor.role()));
        return "account/staff-management";
    }

    @PostMapping("/admin/accounts")
    public String createStaff(@RequestParam(required = false) String loginName,
                              @RequestParam(required = false) String initialPassword,
                              @RequestParam(required = false) String displayName,
                              @RequestParam(required = false) UserRole role,
                              RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return performForm("/admin/accounts", "STAFF_CREATE",
                FormFeedback.safeValues("loginName", loginName, "displayName", displayName, "role", role), redirect,
                () -> accounts.createStaff(actor.role(), loginName, initialPassword, displayName, role),
                "loginName", "initialPassword", "displayName", "role");
    }

    @PostMapping("/admin/accounts/{userId}/disable")
    public String disableStaff(@PathVariable Long userId, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/admin/accounts", redirect, () -> accounts.disableStaff(actor.role(), userId));
    }

    @PostMapping("/admin/accounts/{userId}/reset-password")
    public String resetStaffPassword(@PathVariable Long userId,
                                     @RequestParam(required = false) String newPassword,
                                     RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return performForm("/admin/accounts", "STAFF_RESET_" + userId, FormFeedback.safeValues(), redirect,
                () -> accounts.resetStaffPassword(actor.role(), userId, newPassword), "newPassword");
    }

    @GetMapping("/staff/readers")
    public String readers(@RequestParam(required = false) String q,
                          @RequestParam(required = false) String status,
                          @RequestParam(defaultValue = "0") String page, Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        try {
            ManagementSearch search = ManagementSearch.parse(q, status, page, ManagementQueryService.READER_STATES);
            var result = lists.readers(actor.role(), search);
            ManagementListModel.apply(model, search, result, "/staff/readers", ManagementQueryService.READER_STATES);
            model.addAttribute("readers", result.getContent());
        } catch (IllegalArgumentException exception) {
            ManagementListModel.apply(model, new ManagementSearch("", "ALL", 0), Page.empty(),
                    "/staff/readers", ManagementQueryService.READER_STATES);
            model.addAttribute("readers", List.of());
            model.addAttribute("error", exception.getMessage());
        }
        return "account/reader-management";
    }

    @PostMapping("/staff/readers")
    public String createReader(@RequestParam(required = false) String loginName,
                               @RequestParam(required = false) String initialPassword,
                               @RequestParam(required = false) String displayName,
                               @RequestParam(required = false) String readerNo,
                               @RequestParam(required = false) String contact, @ModelAttribute ManagementReturn listReturn, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return performForm(listReturn.target("/staff/readers", ManagementQueryService.READER_STATES), "READER_CREATE",
                FormFeedback.safeValues("loginName", loginName, "displayName", displayName,
                        "readerNo", readerNo, "contact", contact), redirect,
                () -> accounts.createReader(actor.role(), loginName, initialPassword, displayName, readerNo, contact),
                "loginName", "initialPassword", "displayName", "readerNo", "contact");
    }

    @PostMapping("/staff/readers/{userId}/update")
    public String updateReader(@PathVariable Long userId, @RequestParam(required = false) String displayName,
                               @RequestParam(required = false) String contact, @ModelAttribute ManagementReturn listReturn, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return performForm(listReturn.target("/staff/readers", ManagementQueryService.READER_STATES), "READER_UPDATE_" + userId,
                FormFeedback.safeValues("displayName", displayName, "contact", contact), redirect,
                () -> accounts.updateReader(actor.role(), userId, displayName, contact), "displayName", "contact");
    }

    @PostMapping("/staff/readers/{userId}/disable")
    public String disableReader(@PathVariable Long userId, @ModelAttribute ManagementReturn listReturn, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform(listReturn.target("/staff/readers", ManagementQueryService.READER_STATES), redirect, () -> accounts.disableReader(actor.role(), userId));
    }

    @PostMapping("/staff/readers/{userId}/reset-password")
    public String resetReaderPassword(@PathVariable Long userId,
                                      @RequestParam(required = false) String newPassword,
                                      @ModelAttribute ManagementReturn listReturn, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return performForm(listReturn.target("/staff/readers", ManagementQueryService.READER_STATES), "READER_RESET_" + userId, FormFeedback.safeValues(), redirect,
                () -> accounts.resetReaderPassword(actor.role(), userId, newPassword), "newPassword");
    }

    private static String perform(String target, RedirectAttributes redirect, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", "操作已完成。");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            FormFeedback.failure(redirect, null, exception instanceof IllegalArgumentException
                    ? exception.getMessage() : "操作与现有账号资料冲突，请核对后重试。", null);
        }
        return "redirect:" + target;
    }

    private static String performForm(String target, String formKey, java.util.Map<String, String> safeValues,
                                     RedirectAttributes redirect, Runnable action, String... candidateFields) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", "操作已完成。");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            String message = exception instanceof IllegalArgumentException
                    ? exception.getMessage() : "登录名或读者证号已被使用，请核对后重试。";
            FormFeedback.failure(redirect, formKey, message, safeValues, candidateFields);
        }
        return "redirect:" + target;
    }

}
