package com.example.library.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class MustChangePasswordFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal principal
                && principal.isMustChangePassword()
                && !isAllowedWhilePasswordChangeRequired(request)) {
            response.sendRedirect(request.getContextPath() + "/account/password");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isAllowedWhilePasswordChangeRequired(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/account/password") || path.equals("/logout") || path.equals("/login")
                || path.startsWith("/css/") || path.equals("/js/forms.js") || path.equals("/favicon.ico");
    }
}
