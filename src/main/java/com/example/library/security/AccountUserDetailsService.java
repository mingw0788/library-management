package com.example.library.security;

import com.example.library.account.AppUserRepository;
import com.example.library.account.LoginNameNormalizer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public AccountUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        final String normalized;
        try {
            normalized = LoginNameNormalizer.normalize(username);
        } catch (IllegalArgumentException exception) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return users.findByLoginName(normalized)
                .map(AppUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
