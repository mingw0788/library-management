package com.example.library.loan;

import com.example.library.audit.AuditLogService;
import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.ReaderProfile;
import com.example.library.account.ReaderProfileReference;
import com.example.library.account.ReaderProfileRepository;
import com.example.library.account.UserRole;
import com.example.library.account.UserStatus;
import com.example.library.catalog.Book;
import com.example.library.catalog.BookCopy;
import com.example.library.catalog.BookCopyRepository;
import com.example.library.catalog.BookCopyReference;
import com.example.library.catalog.BookStatus;
import com.example.library.catalog.BookRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LoanCheckoutService {

    private final AppUserRepository users;
    private final ReaderProfileRepository readers;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final LoanRepository loans;
    private final LoanEligibilityPolicy eligibility;
    private final LoanRules rules;
    private final Clock circulationClock;
    private final AuditLogService auditLogs;

    public LoanCheckoutService(AppUserRepository users, ReaderProfileRepository readers, BookRepository books,
                               BookCopyRepository copies, LoanRepository loans,
                               LoanEligibilityPolicy eligibility, LoanRules rules, Clock circulationClock,
                               AuditLogService auditLogs) {
        this.users = users;
        this.readers = readers;
        this.books = books;
        this.copies = copies;
        this.loans = loans;
        this.eligibility = eligibility;
        this.rules = rules;
        this.circulationClock = circulationClock;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public Loan checkout(Long operatorId, UserRole actorRole, String readerNo, String barcode) {
        requireStaff(actorRole);
        if (operatorId == null) throw new IllegalArgumentException("办理人账号无效");
        String normalizedReaderNo = requiredIdentifier(readerNo, "读者证号");
        String normalizedBarcode = requiredIdentifier(barcode, "馆藏条码");

        // 先只读解析输入标识，随后仍会按统一顺序锁行并复核状态。
        ReaderProfileReference observedReader = readers.findReferenceByReaderNo(normalizedReaderNo)
                .orElseThrow(() -> new IllegalArgumentException("读者证号不存在"));
        BookCopyReference observedCopy = copies.findReferenceByBarcode(normalizedBarcode)
                .orElseThrow(() -> new IllegalArgumentException("馆藏条码不存在"));
        Long bookId = observedCopy.bookId();

        Map<Long, AppUser> lockedUsers = lockUsersInOrder(operatorId, observedReader.userId());
        AppUser operator = lockedUsers.get(operatorId);
        if (operator == null || operator.getStatus() != UserStatus.ACTIVE || !isStaff(operator.getRole())
                || operator.getRole() != actorRole) {
            throw new IllegalArgumentException("办理员工账号未启用或无权办理借出");
        }
        AppUser readerAccount = lockedUsers.get(observedReader.userId());
        if (readerAccount == null || readerAccount.getRole() != UserRole.READER
                || readerAccount.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("读者账号未启用，不能办理借阅");
        }

        ReaderProfile reader = readers.findByIdForUpdate(observedReader.id())
                .orElseThrow(() -> new IllegalArgumentException("读者档案不存在"));
        if (!reader.getUserId().equals(readerAccount.getId()) || !reader.getReaderNo().equals(normalizedReaderNo)) {
            throw new IllegalArgumentException("读者资料已发生变化，请重新核对");
        }

        Book book = books.findByIdForUpdate(bookId)
                .orElseThrow(() -> new IllegalArgumentException("书目不存在"));
        if (book.getStatus() != BookStatus.ACTIVE) {
            throw new IllegalArgumentException("书目已归档，不能办理借阅");
        }

        BookCopy copy = copies.findByIdForUpdate(observedCopy.id())
                .orElseThrow(() -> new IllegalArgumentException("馆藏副本不存在"));
        if (!copy.getBookId().equals(book.getId()) || !copy.getBarcode().equals(normalizedBarcode)) {
            throw new IllegalArgumentException("副本资料已发生变化，请重新核对");
        }

        eligibility.assertCanBorrow(reader.getId(), copy.getId());
        Instant borrowedAt = circulationClock.instant();
        Loan loan = loans.saveAndFlush(new Loan(reader, copy, borrowedAt, rules.dueDateFor(borrowedAt), operator));
        copy.checkout();
        copies.saveAndFlush(copy);
        auditLogs.recordCurrent(operatorId, actorRole, "LOAN_CHECKED_OUT", "LOAN", loan.getId(), "办理借出");
        return loan;
    }

    private Map<Long, AppUser> lockUsersInOrder(Long operatorId, Long readerUserId) {
        List<Long> ids = java.util.stream.Stream.of(operatorId, readerUserId)
                .distinct().sorted(Comparator.naturalOrder()).toList();
        Map<Long, AppUser> result = new HashMap<>();
        for (Long id : ids) {
            users.findByIdForUpdate(id).ifPresent(user -> result.put(id, user));
        }
        return result;
    }

    private static String requiredIdentifier(String value, String label) {
        if (value == null) throw new IllegalArgumentException(label + "不能为空");
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 50
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + "长度须为 1—50 个字符且不能包含控制字符");
        }
        return normalized;
    }

    private static boolean isStaff(UserRole role) {
        return role == UserRole.ADMIN || role == UserRole.LIBRARIAN;
    }

    private static void requireStaff(UserRole role) {
        if (!isStaff(role)) throw new AccessDeniedException("仅管理员或馆员可以办理借出");
    }
}
