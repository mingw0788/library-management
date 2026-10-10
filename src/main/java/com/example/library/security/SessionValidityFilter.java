package com.example.library.security;

import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.UserStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SessionValidityFilter extends OncePerRequestFilter {

    private final AppUserRepository users;

    public SessionValidityFilter(AppUserRepository users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal principal
                && !isPublicOrLogoutRequest(request)
                && !isSessionCurrent(principal)) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            SecurityContextHolder.clearContext();
            response.sendRedirect(request.getContextPath() + "/login?expired=true");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isSessionCurrent(AppUserPrincipal principal) {
        return users.findById(principal.getId())
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .map(AppUser::getCredentialVersion)
                .filter(version -> version == principal.getCredentialVersion())
                .isPresent();
    }

    private static boolean isPublicOrLogoutRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/login") || path.equals("/logout") || path.equals("/error")
                || path.startsWith("/css/") || path.equals("/js/forms.js") || path.equals("/favicon.ico");
    }
}
