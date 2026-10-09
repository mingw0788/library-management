package com.example.library.controller;

import com.example.library.account.OwnAccountProfile;
import com.example.library.account.OwnAccountService;
import com.example.library.account.UserRole;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.Set;

@Controller
public class OwnAccountController {

    private static final Set<String> READER_FIELDS = Set.of("displayName", "contact", "_csrf");
    private static final Set<String> STAFF_FIELDS = Set.of("displayName", "_csrf");

    private final OwnAccountService ownAccounts;
    private final CurrentIdentityService currentIdentity;

    public OwnAccountController(OwnAccountService ownAccounts, CurrentIdentityService currentIdentity) {
        this.ownAccounts = ownAccounts;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/account/profile")
    public String profile(@RequestParam(defaultValue = "false") boolean updated, Model model) {
        OwnAccountProfile profile = ownAccounts.getCurrentProfile();
        model.addAttribute("profile", profile);
        model.addAttribute("readerAccount", profile.role() == UserRole.READER);
        model.addAttribute("updated", updated);
        return "account/profile";
    }

    @PostMapping("/account/profile")
    public String updateProfile(@RequestParam String displayName,
                                @RequestParam(required = false) String contact,
                                HttpServletRequest request, Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        if (!containsOnlyAllowedFields(request.getParameterMap(),
                actor.role() == UserRole.READER ? READER_FIELDS : STAFF_FIELDS)) {
            model.addAttribute("errorMessage", "为保护账号安全，只能提交本人允许修改的资料字段。");
        } else {
            try {
                ownAccounts.updateCurrentProfile(displayName, contact);
                return "redirect:/account/profile?updated=true";
            } catch (IllegalArgumentException exception) {
                model.addAttribute("errorMessage", exception.getMessage());
            }
        }
        OwnAccountProfile profile = ownAccounts.getCurrentProfile();
        model.addAttribute("profile", profile);
        model.addAttribute("readerAccount", profile.role() == UserRole.READER);
        model.addAttribute("updated", false);
        return "account/profile";
    }

    private static boolean containsOnlyAllowedFields(Map<String, String[]> parameters, Set<String> allowedFields) {
        return parameters.entrySet().stream().allMatch(entry -> allowedFields.contains(entry.getKey())
                && entry.getValue() != null && entry.getValue().length == 1);
    }
}
