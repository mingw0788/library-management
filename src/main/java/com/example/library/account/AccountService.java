package com.example.library.account;

import com.example.library.audit.AuditLogService;
import com.example.library.reservation.ReservationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ReaderProfileRepository readerProfiles;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogs;
    private final ObjectProvider<ReservationService> reservationService;

    public AccountService(AppUserRepository users, PasswordEncoder passwordEncoder,
                          ReaderProfileRepository readerProfiles, JdbcTemplate jdbcTemplate,
                          AuditLogService auditLogs, ObjectProvider<ReservationService> reservationService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.readerProfiles = readerProfiles;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogs = auditLogs;
        this.reservationService = reservationService;
    }

    @Transactional
    public AppUser createUser(String loginName, String rawPassword, String displayName, UserRole role) {
        if (role == UserRole.ADMIN) {
            lockActiveAdminSet();
        }
        AppUser user = saveUser(loginName, rawPassword, displayName, role);
        auditLogs.recordCurrent(null, "ACCOUNT_CREATED", "USER", user.getId(), "创建账号");
        return user;
    }

    private AppUser saveUser(String loginName, String rawPassword, String displayName, UserRole role) {
        String normalizedLoginName = LoginNameNormalizer.normalize(loginName);
        PasswordPolicy.validate(rawPassword);
        String normalizedDisplayName = normalizeDisplayName(displayName);
        if (role == null) {
            throw new IllegalArgumentException("账号角色不能为空");
        }
        AppUser user = new AppUser(normalizedLoginName, passwordEncoder.encode(rawPassword), normalizedDisplayName, role);
        return users.save(user);
    }

    @Transactional
    public AppUser createStaff(UserRole actorRole, String loginName, String initialPassword,
                               String displayName, UserRole newRole) {
        requireAdmin(actorRole);
        if (newRole != UserRole.ADMIN && newRole != UserRole.LIBRARIAN) {
            throw new IllegalArgumentException("工作人员角色只能是管理员或馆员");
        }
        if (newRole == UserRole.ADMIN) {
            lockActiveAdminSet();
        }
        AppUser user = saveUser(loginName, initialPassword, displayName, newRole);
        auditLogs.recordCurrent(actorRole, "STAFF_CREATED", "USER", user.getId(), "创建工作人员账号");
        return user;
    }

    @Transactional
    public AppUser createReader(UserRole actorRole, String loginName, String initialPassword,
                                String displayName, String readerNo, String contact) {
        requireReaderManager(actorRole);
        String normalizedReaderNo = normalizeReaderNo(readerNo);
        String normalizedContact = normalizeContact(contact);
        if (readerProfiles.existsByReaderNo(normalizedReaderNo)) {
            throw new IllegalArgumentException("读者证号已存在");
        }
        AppUser user = saveUser(loginName, initialPassword, displayName, UserRole.READER);
        readerProfiles.saveAndFlush(new ReaderProfile(user.getId(), normalizedReaderNo, normalizedContact));
        auditLogs.recordCurrent(actorRole, "READER_CREATED", "USER", user.getId(), "创建读者账号");
        return user;
    }

    @Transactional
    public void disableStaff(UserRole actorRole, Long userId) {
        requireAdmin(actorRole);
        AppUser user = requireUser(userId);
        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.LIBRARIAN) {
            throw new IllegalArgumentException("目标账号不是工作人员");
        }
        if (user.getRole() == UserRole.ADMIN && user.getStatus() == UserStatus.ACTIVE) {
            lockActiveAdminSet();
            if (!users.existsByRoleAndStatusAndIdNot(UserRole.ADMIN, UserStatus.ACTIVE, userId)) {
                throw new IllegalArgumentException("系统至少需要一名有效管理员");
            }
        }
        user.changeStatus(UserStatus.DISABLED);
        auditLogs.recordCurrent(actorRole, "STAFF_DISABLED", "USER", user.getId(), "停用工作人员账号");
    }

    @Transactional
    public void resetStaffPassword(UserRole actorRole, Long userId, String newPassword) {
        requireAdmin(actorRole);
        AppUser user = requireStaff(userId);
        PasswordPolicy.validate(newPassword);
        user.replacePasswordHash(passwordEncoder.encode(newPassword));
        auditLogs.recordCurrent(actorRole, "STAFF_PASSWORD_RESET", "USER", user.getId(), "重置工作人员密码");
    }

    @Transactional(readOnly = true)
    public List<ReaderProfile> listReaders(UserRole actorRole) {
        requireReaderManager(actorRole);
        return readerProfiles.findAllByOrderByReaderNoAsc();
    }

    @Transactional(readOnly = true)
    public List<AppUser> listStaff(UserRole actorRole) {
        requireAdmin(actorRole);
        return users.findAll().stream()
                .filter(user -> user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.LIBRARIAN)
                .toList();
    }

    @Transactional
    public void updateReader(UserRole actorRole, Long userId, String displayName, String contact) {
        requireReaderManager(actorRole);
        AppUser user = requireReader(userId);
        user.changeDisplayName(normalizeDisplayName(displayName));
        readerProfiles.findByUserId(userId).orElseThrow(() -> new IllegalArgumentException("读者档案不存在"))
                .changeContact(normalizeContact(contact));
        auditLogs.recordCurrent(actorRole, "READER_UPDATED", "USER", userId, "更新读者资料");
    }

    @Transactional
    public void disableReader(UserRole actorRole, Long userId) {
        requireReaderManager(actorRole);
        if (userId == null) throw new IllegalArgumentException("账号编号不能为空");
        AppUser reader = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        if (reader.getRole() != UserRole.READER) {
            throw new IllegalArgumentException("目标账号不是有效读者");
        }
        var profile = readerProfiles.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("目标账号不是有效读者"));
        reader.changeStatus(UserStatus.DISABLED);
        auditLogs.recordCurrent(actorRole, "READER_DISABLED", "USER", reader.getId(), "停用读者账号");
        reservationService.ifAvailable(service -> service.cancelForDisabledReader(profile.getId(), actorRole));
    }

    @Transactional
    public void resetReaderPassword(UserRole actorRole, Long userId, String newPassword) {
        requireReaderManager(actorRole);
        PasswordPolicy.validate(newPassword);
        AppUser reader = requireReader(userId);
        reader.replacePasswordHash(passwordEncoder.encode(newPassword));
        auditLogs.recordCurrent(actorRole, "READER_PASSWORD_RESET", "USER", reader.getId(), "重置读者密码");
    }

    @Transactional
    public boolean initializeFirstAdmin(String loginName, String password, String displayName) {
        lockActiveAdminSet();
        if (users.existsByRole(UserRole.ADMIN)) {
            return false;
        }
        AppUser admin = saveUser(loginName, password, displayName, UserRole.ADMIN);
        auditLogs.recordSystem("INITIAL_ADMIN_CREATED", "USER", admin.getId(), "初始化首位管理员");
        return true;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<AppUser> findByLoginName(String loginName) {
        return users.findByLoginName(LoginNameNormalizer.normalize(loginName));
    }

    @Transactional
    public void changeOwnPassword(String loginName, String currentPassword, String newPassword) {
        AppUser user = users.findByLoginName(LoginNameNormalizer.normalize(loginName))
                .orElseThrow(() -> new IllegalArgumentException("当前账号不存在"));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        PasswordPolicy.validate(newPassword);
        user.completePasswordChange(passwordEncoder.encode(newPassword));
        auditLogs.recordCurrent(user.getId(), user.getRole(), "OWN_PASSWORD_CHANGED", "USER", user.getId(),
                "本人修改密码");
    }

    private static String normalizeDisplayName(String value) {
        if (value == null) {
            throw new IllegalArgumentException("姓名不能为空");
        }
        String normalized = value.trim();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints < 1 || codePoints > 100) {
            throw new IllegalArgumentException("姓名长度须为 1—100 个字符");
        }
        return normalized;
    }

    private void lockActiveAdminSet() {
        jdbcTemplate.queryForObject("SELECT guard_key FROM system_guard WHERE guard_key = 'ACTIVE_ADMIN_SET' FOR UPDATE",
                String.class);
    }

    private AppUser requireUser(Long userId) {
        if (userId == null) throw new IllegalArgumentException("账号编号不能为空");
        return users.findById(userId).orElseThrow(() -> new IllegalArgumentException("账号不存在"));
    }

    private AppUser requireStaff(Long userId) {
        AppUser user = requireUser(userId);
        if (user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.LIBRARIAN) {
            throw new IllegalArgumentException("目标账号不是工作人员");
        }
        return user;
    }

    private AppUser requireReader(Long userId) {
        AppUser user = requireUser(userId);
        if (user.getRole() != UserRole.READER || !readerProfiles.findByUserId(userId).isPresent()) {
            throw new IllegalArgumentException("目标账号不是有效读者");
        }
        return user;
    }

    private static void requireAdmin(UserRole role) {
        if (role != UserRole.ADMIN) throw new AccessDeniedException("仅管理员可以管理工作人员账号");
    }

    private static void requireReaderManager(UserRole role) {
        if (role != UserRole.ADMIN && role != UserRole.LIBRARIAN) {
            throw new AccessDeniedException("仅管理员或馆员可以管理读者账号");
        }
    }

    private static String normalizeReaderNo(String value) {
        if (value == null) throw new IllegalArgumentException("读者证号不能为空");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 50) {
            throw new IllegalArgumentException("读者证号长度须为 1—50 个字符");
        }
        return normalized;
    }

    private static String normalizeContact(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw new IllegalArgumentException("联系方式不能超过 100 个字符");
        }
        return normalized;
    }
}
