package com.example.library.audit;

import com.example.library.account.UserRole;
import com.example.library.security.AppUserPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class AuditLogService {

    private static final int PAGE_SIZE = 25;
    private static final int MAX_PAGE = 100_000;

    private final AuditLogRepository auditLogs;
    private final Clock clock;

    public AuditLogService(AuditLogRepository auditLogs, Clock circulationClock) {
        this.auditLogs = auditLogs;
        this.clock = circulationClock;
    }

    /**
     * Called at the end of a business transaction. The audit insert joins that transaction,
     * so a failure here rolls back both records together.
     */
    @Transactional
    public void recordCurrent(UserRole expectedRole, String action, String targetType, Long targetId, String summary) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserPrincipal actor)) {
            // Direct service tests and system jobs may have no web identity. Bootstrap has its own explicit path.
            return;
        }
        if (expectedRole != null && actor.getRole() != expectedRole) {
            throw new AccessDeniedException("审计操作者角色与当前身份不一致");
        }
        save(actor.getId(), actor.getUsername(), actor.getRole().name(), action, targetType, targetId, summary);
    }

    @Transactional
    public void recordCurrent(Long expectedActorId, UserRole expectedRole, String action,
                              String targetType, Long targetId, String summary) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AppUserPrincipal actor)) {
            return;
        }
        if ((expectedActorId != null && !expectedActorId.equals(actor.getId()))
                || (expectedRole != null && actor.getRole() != expectedRole)) {
            throw new AccessDeniedException("审计操作者与当前身份不一致");
        }
        save(actor.getId(), actor.getUsername(), actor.getRole().name(), action, targetType, targetId, summary);
    }

    @Transactional
    public void recordSystem(String action, String targetType, Long targetId, String summary) {
        save(null, "SYSTEM", "SYSTEM", action, targetType, targetId, summary);
    }

    @Transactional
    public void recordCurrentOrSystem(String action, String targetType, Long targetId, String summary) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AppUserPrincipal) {
            recordCurrent((UserRole) null, action, targetType, targetId, summary);
        } else {
            recordSystem(action, targetType, targetId, summary);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(UserRole actorRole, int requestedPage) {
        if (actorRole != UserRole.ADMIN) throw new AccessDeniedException("仅管理员可以查看操作审计");
        int page = Math.max(1, Math.min(requestedPage, MAX_PAGE));
        Sort order = Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"));
        Page<AuditLog> result = auditLogs.findAll(PageRequest.of(page - 1, PAGE_SIZE, order));
        if (result.getTotalPages() > 0 && page > result.getTotalPages()) {
            return auditLogs.findAll(PageRequest.of(result.getTotalPages() - 1, PAGE_SIZE, order));
        }
        return result;
    }

    private void save(Long actorId, String loginName, String role, String action,
                      String targetType, Long targetId, String summary) {
        auditLogs.saveAndFlush(new AuditLog(actorId, loginName, role, action,
                targetType, targetId, "SUCCESS", summary, clock.instant()));
    }
}
