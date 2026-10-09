package com.example.library.account;

import com.example.library.audit.AuditLogService;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnAccountService {

    private final AppUserRepository users;
    private final ReaderProfileRepository readerProfiles;
    private final CurrentIdentityService currentIdentity;
    private final AuditLogService auditLogs;

    public OwnAccountService(AppUserRepository users, ReaderProfileRepository readerProfiles,
                             CurrentIdentityService currentIdentity, AuditLogService auditLogs) {
        this.users = users;
        this.readerProfiles = readerProfiles;
        this.currentIdentity = currentIdentity;
        this.auditLogs = auditLogs;
    }

    @Transactional(readOnly = true)
    public OwnAccountProfile getCurrentProfile() {
        CurrentIdentity identity = currentIdentity.requireCurrentIdentity();
        AppUser user = requireCurrentUser(identity);
        ReaderProfile reader = identity.role() == UserRole.READER ? requireReaderProfile(user.getId()) : null;
        return new OwnAccountProfile(user.getLoginName(), user.getDisplayName(), user.getRole(), user.getStatus(),
                reader == null ? null : reader.getReaderNo(), reader == null ? null : reader.getContact());
    }

    @Transactional
    public void updateCurrentProfile(String displayName, String contact) {
        CurrentIdentity identity = currentIdentity.requireCurrentIdentity();
        AppUser user = users.findByIdForUpdate(identity.userId())
                .orElseThrow(() -> new AccessDeniedException("当前账号不存在"));
        assertCurrentActiveAccount(identity, user);

        String normalizedName = normalizeDisplayName(displayName);
        String normalizedContact = null;
        ReaderProfile reader = null;
        if (identity.role() == UserRole.READER) {
            ReaderProfile observed = requireReaderProfile(user.getId());
            reader = readerProfiles.findByIdForUpdate(observed.getId())
                    .orElseThrow(() -> new IllegalArgumentException("读者档案不存在"));
            normalizedContact = normalizeContact(contact);
        } else if (contact != null && !contact.isBlank()) {
            throw new IllegalArgumentException("工作人员资料不包含可编辑的联系方式");
        }

        user.changeDisplayName(normalizedName);
        if (reader != null) reader.changeContact(normalizedContact);
        auditLogs.recordCurrent(identity.userId(), identity.role(), "OWN_PROFILE_UPDATED", "USER", user.getId(),
                "本人更新资料");
    }

    private AppUser requireCurrentUser(CurrentIdentity identity) {
        AppUser user = users.findById(identity.userId())
                .orElseThrow(() -> new AccessDeniedException("当前账号不存在"));
        assertCurrentActiveAccount(identity, user);
        return user;
    }

    private ReaderProfile requireReaderProfile(Long userId) {
        return readerProfiles.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("读者档案不存在"));
    }

    private static void assertCurrentActiveAccount(CurrentIdentity identity, AppUser user) {
        if (user.getStatus() != UserStatus.ACTIVE || user.getRole() != identity.role()) {
            throw new AccessDeniedException("登录状态已变化，请重新登录");
        }
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) throw new IllegalArgumentException("姓名不能为空");
        String normalized = value.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > 100 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("姓名长度须为 1—100 个字符且不能包含控制字符");
        }
        return normalized;
    }

    private static String normalizeContact(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > 100
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("联系方式不能超过 100 个字符或包含控制字符");
        }
        return normalized;
    }
}
