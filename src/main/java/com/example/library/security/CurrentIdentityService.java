package com.example.library.security;

import com.example.library.account.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentIdentityService {

    public CurrentIdentity requireCurrentIdentity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new AccessDeniedException("需要有效的登录身份");
        }
        return new CurrentIdentity(principal.getId(), principal.getUsername(), principal.getRole());
    }

    public Long requireReaderUserId() {
        CurrentIdentity identity = requireCurrentIdentity();
        if (identity.role() != UserRole.READER) {
            throw new AccessDeniedException("当前操作仅限读者账号");
        }
        return identity.userId();
    }

    public void requireOwnReaderUserId(Long requestedUserId) {
        Long currentReaderUserId = requireReaderUserId();
        if (requestedUserId == null || !currentReaderUserId.equals(requestedUserId)) {
            throw new AccessDeniedException("不能访问其他读者的资料");
        }
    }
}
