package com.example.library.security;

import com.example.library.account.AppUserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import com.example.library.security.SafeRequestLoggingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class SecurityConfiguration {

    private static final Logger securityLog = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http, AppUserRepository appUserRepository) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/", "/login", "/css/**", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                        .requestMatchers("/staff", "/staff/**").hasAnyRole("ADMIN", "LIBRARIAN")
                        .requestMatchers("/reader", "/reader/**").hasRole("READER")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .failureHandler((request, response, exception) -> {
                            securityLog.warn("Authentication failure method={} path={}",
                                    request.getMethod(), request.getRequestURI());
                            response.sendRedirect(request.getContextPath() + "/login?error=true");
                        })
                        .defaultSuccessUrl("/", false)
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            securityLog.warn("Authentication required method={} path={}",
                                    request.getMethod(), request.getRequestURI());
                            new LoginUrlAuthenticationEntryPoint("/login").commence(request, response, exception);
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            securityLog.warn("Access denied method={} path={}",
                                    request.getMethod(), request.getRequestURI());
                            response.sendError(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                        }));
        http.addFilterBefore(new SafeRequestLoggingFilter(), AuthorizationFilter.class);
        http.addFilterAfter(new MustChangePasswordFilter(), AuthorizationFilter.class);
        http.addFilterBefore(new SessionValidityFilter(appUserRepository), AuthorizationFilter.class);
        return http.build();
    }
}
