package com.example.library.security;

import com.example.library.account.UserRole;

public record CurrentIdentity(Long userId, String loginName, UserRole role) {
}
