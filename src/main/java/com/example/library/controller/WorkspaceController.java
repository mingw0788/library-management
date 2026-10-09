package com.example.library.controller;

import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WorkspaceController {

    private final CurrentIdentityService currentIdentity;

    public WorkspaceController(CurrentIdentityService currentIdentity) {
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/admin")
    public String adminWorkspace(Model model) {
        CurrentIdentity identity = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", identity.loginName());
        return "workspace/admin";
    }

    @GetMapping("/staff")
    public String staffWorkspace(Model model) {
        CurrentIdentity identity = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", identity.loginName());
        return "workspace/staff";
    }

    @GetMapping("/reader")
    public String readerWorkspace(Model model) {
        currentIdentity.requireReaderUserId();
        CurrentIdentity identity = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", identity.loginName());
        return "workspace/reader";
    }
}
