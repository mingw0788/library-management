package com.example.library.security;

import com.example.library.account.AppUser;
import com.example.library.account.UserRole;
import com.example.library.account.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public final class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String loginName;
    private final String passwordHash;
    private final UserRole role;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final long credentialVersion;

    public AppUserPrincipal(AppUser user) {
        this.id = user.getId();
        this.loginName = user.getLoginName();
        this.passwordHash = user.getPasswordHash();
        this.role = user.getRole();
        this.enabled = user.getStatus() == UserStatus.ACTIVE;
        this.mustChangePassword = user.isMustChangePassword();
        this.credentialVersion = user.getCredentialVersion();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return loginName; }

    @Override
    public boolean isEnabled() { return enabled; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    public Long getId() { return id; }

    public boolean isMustChangePassword() { return mustChangePassword; }

    public long getCredentialVersion() { return credentialVersion; }

    public UserRole getRole() { return role; }
}
