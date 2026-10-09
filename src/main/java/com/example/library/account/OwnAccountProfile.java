package com.example.library.account;

public record OwnAccountProfile(String loginName, String displayName, UserRole role, UserStatus status,
                                String readerNo, String contact) {}
