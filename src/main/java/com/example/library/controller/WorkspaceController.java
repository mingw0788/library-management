package com.example.library.controller;

import com.example.library.dashboard.DashboardStatisticsService;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WorkspaceController {

    private final CurrentIdentityService currentIdentity;
    private final DashboardStatisticsService dashboardStatistics;

    public WorkspaceController(CurrentIdentityService currentIdentity, DashboardStatisticsService dashboardStatistics) {
        this.currentIdentity = currentIdentity;
        this.dashboardStatistics = dashboardStatistics;
    }

    @GetMapping("/admin")
    public String adminWorkspace(Model model) {
        CurrentIdentity identity = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", identity.loginName());
        model.addAttribute("staffStats", dashboardStatistics.staffStatistics());
        return "workspace/admin";
    }

    @GetMapping("/staff")
    public String staffWorkspace(Model model) {
        CurrentIdentity identity = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", identity.loginName());
        model.addAttribute("staffStats", dashboardStatistics.staffStatistics());
        return "workspace/staff";
    }

    @GetMapping("/reader")
    public String readerWorkspace(Model model) {
        currentIdentity.requireReaderUserId();
        CurrentIdentity identity = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", identity.loginName());
        model.addAttribute("readerStats", dashboardStatistics.readerStatistics());
        return "workspace/reader";
    }
}
