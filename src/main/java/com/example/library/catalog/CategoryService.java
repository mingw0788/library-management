package com.example.library.catalog;

import com.example.library.audit.AuditLogService;
import com.example.library.account.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final AuditLogService auditLogs;

    public CategoryService(CategoryRepository categories, AuditLogService auditLogs) {
        this.categories = categories;
        this.auditLogs = auditLogs;
    }

    @Transactional(readOnly = true)
    public List<Category> search(UserRole actorRole, String query) {
        requireStaff(actorRole);
        if (query == null || query.trim().isEmpty()) return categories.findAllByOrderByNameAsc();
        String normalized = query.trim();
        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw new IllegalArgumentException("查询词不能超过 100 个字符");
        }
        return categories.findByNameContainingOrderByNameAsc(normalized);
    }

    @Transactional
    public Category create(UserRole actorRole, String name) {
        requireStaff(actorRole);
        String normalized = normalizeName(name);
        if (categories.findByName(normalized).isPresent()) {
            throw new IllegalArgumentException("分类名称已存在");
        }
        Category category = categories.saveAndFlush(new Category(normalized));
        auditLogs.recordCurrent(actorRole, "CATEGORY_CREATED", "CATEGORY", category.getId(), "新增分类");
        return category;
    }

    @Transactional
    public Category rename(UserRole actorRole, Long id, String name) {
        requireStaff(actorRole);
        String normalized = normalizeName(name);
        Category category = requireCategory(id);
        categories.findByName(normalized).filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> { throw new IllegalArgumentException("分类名称已存在"); });
        category.rename(normalized);
        Category saved = categories.saveAndFlush(category);
        auditLogs.recordCurrent(actorRole, "CATEGORY_RENAMED", "CATEGORY", saved.getId(), "修改分类名称");
        return saved;
    }

    @Transactional
    public void delete(UserRole actorRole, Long id) {
        requireStaff(actorRole);
        categories.delete(requireCategory(id));
        categories.flush();
        auditLogs.recordCurrent(actorRole, "CATEGORY_DELETED", "CATEGORY", id, "删除分类");
    }

    private Category requireCategory(Long id) {
        if (id == null) throw new IllegalArgumentException("分类编号不能为空");
        return categories.findById(id).orElseThrow(() -> new IllegalArgumentException("分类不存在"));
    }

    private static String normalizeName(String name) {
        if (name == null) throw new IllegalArgumentException("分类名称不能为空");
        String normalized = name.trim();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints < 1 || codePoints > 100) {
            throw new IllegalArgumentException("分类名称长度须为 1—100 个字符");
        }
        return normalized;
    }

    private static void requireStaff(UserRole role) {
        if (role != UserRole.ADMIN && role != UserRole.LIBRARIAN) {
            throw new AccessDeniedException("仅管理员或馆员可以管理分类");
        }
    }
}
