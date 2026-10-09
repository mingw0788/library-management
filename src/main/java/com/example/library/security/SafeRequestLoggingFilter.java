package com.example.library.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Logs only the request method, path, status, and exception type; never logs a body or credentials. */
public class SafeRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger requestLog = LoggerFactory.getLogger(SafeRequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            requestLog.warn("Request processing failed method={} path={} errorType={}",
                    request.getMethod(), request.getRequestURI(), exception.getClass().getSimpleName());
            throw exception;
        }
        int status = response.getStatus();
        if (status >= 400) {
            requestLog.warn("Request returned error method={} path={} status={}",
                    request.getMethod(), request.getRequestURI(), status);
        }
    }
}
