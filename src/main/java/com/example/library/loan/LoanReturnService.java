package com.example.library.loan;

import com.example.library.audit.AuditLogService;
import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.ReaderProfile;
import com.example.library.account.ReaderProfileRepository;
import com.example.library.account.UserRole;
import com.example.library.account.UserStatus;
import com.example.library.catalog.Book;
import com.example.library.catalog.BookCopy;
import com.example.library.catalog.BookCopyRepository;
import com.example.library.catalog.BookCopyStatus;
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
import java.util.stream.Stream;

@Service
public class LoanReturnService {

    private final AppUserRepository users;
    private final ReaderProfileRepository readers;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final LoanRepository loans;
    private final Clock circulationClock;
    private final AuditLogService auditLogs;

    public LoanReturnService(AppUserRepository users, ReaderProfileRepository readers, BookRepository books,
                             BookCopyRepository copies, LoanRepository loans, Clock circulationClock,
                             AuditLogService auditLogs) {
        this.users = users;
        this.readers = readers;
        this.books = books;
        this.copies = copies;
        this.loans = loans;
        this.circulationClock = circulationClock;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public Loan returnByLoanId(Long operatorId, UserRole actorRole, Long loanId) {
        requireStaff(actorRole);
        if (operatorId == null) throw new IllegalArgumentException("办理人账号无效");
        if (loanId == null || loanId < 1) throw new IllegalArgumentException("借阅编号无效");
        LoanReference reference = loans.findActiveReferenceById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("借阅记录不存在或已经归还"));
        return returnLocked(operatorId, actorRole, reference);
    }

    @Transactional
    public Loan returnByBarcode(Long operatorId, UserRole actorRole, String barcode) {
        requireStaff(actorRole);
        if (operatorId == null) throw new IllegalArgumentException("办理人账号无效");
        String normalizedBarcode = requiredBarcode(barcode);
        LoanReference reference = loans.findActiveReferenceByBarcode(normalizedBarcode)
                .orElseThrow(() -> new IllegalArgumentException("该副本没有未归还借阅记录"));
        return returnLocked(operatorId, actorRole, reference);
    }

    private Loan returnLocked(Long operatorId, UserRole actorRole, LoanReference reference) {
        Map<Long, AppUser> lockedUsers = lockUsersInOrder(operatorId, reference.readerUserId());
        AppUser operator = lockedUsers.get(operatorId);
        if (operator == null || operator.getStatus() != UserStatus.ACTIVE || !isStaff(operator.getRole())
                || operator.getRole() != actorRole) {
            throw new IllegalArgumentException("办理员工账号未启用或无权办理归还");
        }
        AppUser readerAccount = lockedUsers.get(reference.readerUserId());
        // 禁用读者不能再借，但仍可由工作人员归还已有馆藏。
        if (readerAccount == null || readerAccount.getRole() != UserRole.READER) {
            throw new IllegalArgumentException("借阅关联的读者账号不存在或资料异常");
        }

        ReaderProfile reader = readers.findByIdForUpdate(reference.readerId())
                .orElseThrow(() -> new IllegalArgumentException("借阅关联的读者档案不存在"));
        if (!reader.getUserId().equals(reference.readerUserId())) {
            throw new IllegalArgumentException("借阅关联的读者资料已发生变化");
        }

        Book book = books.findByIdForUpdate(reference.bookId())
                .orElseThrow(() -> new IllegalArgumentException("借阅关联的书目不存在"));
        BookCopy copy = copies.findByIdForUpdate(reference.copyId())
                .orElseThrow(() -> new IllegalArgumentException("借阅关联的副本不存在"));
        if (!copy.getBookId().equals(book.getId())) {
            throw new IllegalArgumentException("借阅关联的书目和副本不一致");
        }

        Loan loan = loans.findByIdForUpdate(reference.loanId())
                .orElseThrow(() -> new IllegalArgumentException("借阅记录不存在"));
        if (loan.getReturnedAt() != null) {
            throw new IllegalArgumentException("该借阅已经归还，请勿重复办理");
        }
        if (!loan.getReaderId().equals(reference.readerId()) || !loan.getCopyId().equals(reference.copyId())) {
            throw new IllegalArgumentException("借阅记录关联资料已发生变化，请重新核对");
        }
        if (copy.getStatus() != BookCopyStatus.ON_LOAN) {
            throw new IllegalArgumentException("借阅记录与副本状态不一致，无法办理归还");
        }

        Instant returnedAt = circulationClock.instant();
        loan.returnTo(returnedAt, operator);
        copy.returnFromLoan();
        loans.saveAndFlush(loan);
        copies.saveAndFlush(copy);
        auditLogs.recordCurrent(operatorId, actorRole, "LOAN_RETURNED", "LOAN", loan.getId(), "办理归还");
        return loan;
    }

    private Map<Long, AppUser> lockUsersInOrder(Long operatorId, Long readerUserId) {
        List<Long> ids = Stream.of(operatorId, readerUserId).distinct().sorted(Comparator.naturalOrder()).toList();
        Map<Long, AppUser> result = new HashMap<>();
        for (Long id : ids) users.findByIdForUpdate(id).ifPresent(user -> result.put(id, user));
        return result;
    }

    private static String requiredBarcode(String barcode) {
        if (barcode == null) throw new IllegalArgumentException("馆藏条码不能为空");
        String normalized = barcode.trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 50
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("馆藏条码长度须为 1—50 个字符且不能包含控制字符");
        }
        return normalized;
    }

    private static boolean isStaff(UserRole role) {
        return role == UserRole.ADMIN || role == UserRole.LIBRARIAN;
    }

    private static void requireStaff(UserRole role) {
        if (!isStaff(role)) throw new AccessDeniedException("仅管理员或馆员可以办理归还");
    }
}
