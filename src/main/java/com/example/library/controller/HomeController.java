package com.example.library.controller;

import com.example.library.catalog.CatalogService;
import com.example.library.dashboard.ReaderReminderService;
import com.example.library.account.UserRole;
import com.example.library.security.AppUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final CatalogService catalog;
    private final ReaderReminderService reminders;

    public HomeController(CatalogService catalog, ReaderReminderService reminders) {
        this.catalog = catalog;
        this.reminders = reminders;
    }

    @GetMapping("/")
    public String index(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal actor) {
            model.addAttribute("homeCatalog", catalog.search(null, null, 0, 6, "titleAsc"));
            model.addAttribute("homeInventory", catalog.overview());
            if (actor.getRole() == UserRole.READER) {
                model.addAttribute("readerReminders", reminders.forReader(actor.getId()));
            }
        }
        return "index";
    }
}
