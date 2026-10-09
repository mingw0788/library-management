package com.example.library.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(name = "uk_app_user_login_name", columnNames = "login_name"))
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_name", nullable = false, length = 50, updatable = false)
    private String loginName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected AppUser() {
    }

    AppUser(String loginName, String passwordHash, String displayName, UserRole role) {
        this.loginName = loginName;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.mustChangePassword = true;
        this.credentialVersion = 0;
    }

    public void changeStatus(UserStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("账号状态不能为空");
        }
        this.status = status;
        incrementCredentialVersion();
    }

    public void replacePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("密码编码不能为空");
        }
        this.passwordHash = passwordHash;
        this.mustChangePassword = true;
        incrementCredentialVersion();
    }

    public void completePasswordChange(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("密码编码不能为空");
        }
        this.passwordHash = passwordHash;
        this.mustChangePassword = false;
        incrementCredentialVersion();
    }

    public void markPasswordChanged() {
        this.mustChangePassword = false;
        incrementCredentialVersion();
    }

    private void incrementCredentialVersion() {
        this.credentialVersion = Math.addExact(this.credentialVersion, 1);
    }

    @PrePersist
    @PreUpdate
    private void validatePersistedState() {
        if (loginName == null || !loginName.equals(LoginNameNormalizer.normalize(loginName))) {
            throw new IllegalStateException("登录名必须使用归一化格式");
        }
        if (passwordHash == null || passwordHash.isBlank() || displayName == null || displayName.isBlank()
                || displayName.codePointCount(0, displayName.length()) > 100 || role == null || status == null
                || credentialVersion < 0) {
            throw new IllegalStateException("账号数据不符合约束");
        }
    }

    public Long getId() { return id; }
    public String getLoginName() { return loginName; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public long getCredentialVersion() { return credentialVersion; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
