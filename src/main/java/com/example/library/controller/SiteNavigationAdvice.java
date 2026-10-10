package com.example.library.controller;

import com.example.library.reservation.ReservationService;
import com.example.library.security.AppUserPrincipal;
import com.example.library.web.SiteNavigation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class SiteNavigationAdvice {

    private final ObjectProvider<ReservationService> reservations;

    public SiteNavigationAdvice(ObjectProvider<ReservationService> reservations) {
        this.reservations = reservations;
    }

    @ModelAttribute("siteNav")
    public SiteNavigation navigation(Authentication authentication, HttpServletRequest request) {
        String currentPath = request.getRequestURI().substring(request.getContextPath().length());
        boolean reservationsEnabled = reservations.getIfAvailable() != null;
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            return new SiteNavigation(false, "", "", "", "", "", reservationsEnabled, currentPath);
        }

        String roleLabel = switch (principal.getRole()) {
            case ADMIN -> "管理员";
            case LIBRARIAN -> "馆员";
            case READER -> "读者";
        };
        String workspacePath = switch (principal.getRole()) {
            case ADMIN -> "/admin";
            case LIBRARIAN -> "/staff";
            case READER -> "/reader";
        };
        String workspaceLabel = switch (principal.getRole()) {
            case ADMIN -> "管理员工作区";
            case LIBRARIAN -> "馆员工作区";
            case READER -> "我的工作区";
        };
        return new SiteNavigation(true, principal.getUsername(), principal.getRole().name(), roleLabel,
                workspacePath, workspaceLabel, reservationsEnabled, currentPath);
    }
}
