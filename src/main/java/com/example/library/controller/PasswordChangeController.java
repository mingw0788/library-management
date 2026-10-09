package com.example.library.controller;

import com.example.library.account.AccountService;
import com.example.library.security.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import com.example.library.web.FormFeedback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordChangeController {

    private final AccountService accounts;

    public PasswordChangeController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/account/password")
    public String passwordForm() {
        return "account/password";
    }

    @PostMapping("/account/password")
    public String changePassword(@AuthenticationPrincipal AppUserPrincipal principal,
                                 @RequestParam(required = false) String currentPassword,
                                 @RequestParam(required = false) String newPassword,
                                 @RequestParam(required = false) String confirmPassword,
                                 Authentication authentication,
                                 HttpServletRequest request,
                                 HttpServletResponse response,
                                 Model model) {
        if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "两次输入的新密码不一致。");
            model.addAttribute("formKey", "PASSWORD_CHANGE");
            model.addAttribute("fieldErrors", java.util.Map.of(
                    "newPassword", "两次输入的新密码不一致。",
                    "confirmPassword", "两次输入的新密码不一致。"));
            return "account/password";
        }
        try {
            accounts.changeOwnPassword(principal.getUsername(), currentPassword, newPassword);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
            model.addAttribute("formKey", "PASSWORD_CHANGE");
            String field = exception.getMessage().contains("当前密码") ? "currentPassword" : "newPassword";
            model.addAttribute("fieldErrors", FormFeedback.fieldErrors(field, exception.getMessage()));
            return "account/password";
        }
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?changed=true";
    }
}
