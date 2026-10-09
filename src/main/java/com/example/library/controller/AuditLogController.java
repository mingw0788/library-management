package com.example.library.controller;

import com.example.library.audit.AuditLogService;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuditLogController {

    private final AuditLogService auditLogs;
    private final CurrentIdentityService currentIdentity;

    public AuditLogController(AuditLogService auditLogs, CurrentIdentityService currentIdentity) {
        this.auditLogs = auditLogs;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/admin/audit")
    public String audit(@RequestParam(defaultValue = "1") int page, Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        var result = auditLogs.search(actor.role(), page);
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("auditPage", result);
        model.addAttribute("pageNumber", result.getNumber() + 1);
        return "audit/list";
    }
}
