package com.example.library.controller;

import com.example.library.account.AccountService;
import com.example.library.account.AppUserRepository;
import com.example.library.account.ReaderProfile;
import com.example.library.account.UserRole;
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
public class AccountManagementController {

    private final AccountService accounts;
    private final AppUserRepository users;
    private final CurrentIdentityService currentIdentity;

    public AccountManagementController(AccountService accounts, AppUserRepository users,
                                       CurrentIdentityService currentIdentity) {
        this.accounts = accounts;
        this.users = users;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/admin/accounts")
    public String staffAccounts(Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("staff", accounts.listStaff(actor.role()));
        return "account/staff-management";
    }

    @PostMapping("/admin/accounts")
    public String createStaff(@RequestParam String loginName, @RequestParam String initialPassword,
                              @RequestParam String displayName, @RequestParam UserRole role,
                              RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/admin/accounts", redirect, () ->
                accounts.createStaff(actor.role(), loginName, initialPassword, displayName, role));
    }

    @PostMapping("/admin/accounts/{userId}/disable")
    public String disableStaff(@PathVariable Long userId, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/admin/accounts", redirect, () -> accounts.disableStaff(actor.role(), userId));
    }

    @PostMapping("/admin/accounts/{userId}/reset-password")
    public String resetStaffPassword(@PathVariable Long userId, @RequestParam String newPassword,
                                     RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/admin/accounts", redirect, () -> accounts.resetStaffPassword(actor.role(), userId, newPassword));
    }

    @GetMapping("/staff/readers")
    public String readers(Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        List<ReaderProfile> profiles = accounts.listReaders(actor.role());
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("readers", profiles.stream().map(profile -> new ReaderRow(
                profile.getUserId(), profile.getReaderNo(),
                users.findById(profile.getUserId()).map(user -> user.getDisplayName()).orElse("（账号缺失）"),
                profile.getContact(), users.findById(profile.getUserId()).map(user -> user.getStatus().name()).orElse("未知")))
                .toList());
        return "account/reader-management";
    }

    @PostMapping("/staff/readers")
    public String createReader(@RequestParam String loginName, @RequestParam String initialPassword,
                               @RequestParam String displayName, @RequestParam String readerNo,
                               @RequestParam(required = false) String contact, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/staff/readers", redirect, () ->
                accounts.createReader(actor.role(), loginName, initialPassword, displayName, readerNo, contact));
    }

    @PostMapping("/staff/readers/{userId}/update")
    public String updateReader(@PathVariable Long userId, @RequestParam String displayName,
                               @RequestParam(required = false) String contact, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/staff/readers", redirect, () -> accounts.updateReader(actor.role(), userId, displayName, contact));
    }

    @PostMapping("/staff/readers/{userId}/disable")
    public String disableReader(@PathVariable Long userId, RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/staff/readers", redirect, () -> accounts.disableReader(actor.role(), userId));
    }

    @PostMapping("/staff/readers/{userId}/reset-password")
    public String resetReaderPassword(@PathVariable Long userId, @RequestParam String newPassword,
                                      RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        return perform("/staff/readers", redirect, () -> accounts.resetReaderPassword(actor.role(), userId, newPassword));
    }

    private static String perform(String target, RedirectAttributes redirect, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("notice", "操作已完成。");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("error", exception instanceof IllegalArgumentException
                    ? exception.getMessage() : "登录名或读者证号已被使用，请核对后重试。");
        }
        return "redirect:" + target;
    }

    public record ReaderRow(Long userId, String readerNo, String displayName, String contact, String status) {}
}
