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

@Service
public class LoanRenewalService {

    private static final int RENEWAL_EXTENSION_DAYS = 14;

    private final AppUserRepository users;
    private final ReaderProfileRepository readers;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final LoanRepository loans;
    private final LoanRules rules;
    private final ReservationRenewalGuard reservationGuard;
    private final AuditLogService auditLogs;

    public LoanRenewalService(AppUserRepository users, ReaderProfileRepository readers, BookRepository books,
                              BookCopyRepository copies, LoanRepository loans, LoanRules rules,
                              ReservationRenewalGuard reservationGuard, AuditLogService auditLogs) {
        this.users = users;
        this.readers = readers;
        this.books = books;
        this.copies = copies;
        this.loans = loans;
        this.rules = rules;
        this.reservationGuard = reservationGuard;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public Loan renewForReader(Long authenticatedReaderUserId, Long loanId) {
        if (authenticatedReaderUserId == null) throw new AccessDeniedException("需要有效的读者身份");
        if (loanId == null || loanId < 1) throw new IllegalArgumentException("借阅编号无效");

        // Resolve only stable IDs without a lock. Ownership and status are checked again after locking.
        LoanReference reference = loans.findActiveReferenceById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("借阅记录不存在或已经归还"));
        if (!authenticatedReaderUserId.equals(reference.readerUserId())) {
            throw new AccessDeniedException("不能续借他人的借阅记录");
        }

        AppUser readerAccount = users.findByIdForUpdate(authenticatedReaderUserId)
                .orElseThrow(() -> new IllegalArgumentException("读者账号不存在"));
        if (readerAccount.getRole() != UserRole.READER || readerAccount.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("读者账号未启用，不能续借");
        }

        ReaderProfile reader = readers.findByIdForUpdate(reference.readerId())
                .orElseThrow(() -> new IllegalArgumentException("读者档案不存在"));
        if (!reader.getUserId().equals(authenticatedReaderUserId)) {
            throw new IllegalArgumentException("借阅关联的读者资料已发生变化");
        }

        Book book = books.findByIdForUpdate(reference.bookId())
                .orElseThrow(() -> new IllegalArgumentException("借阅关联的书目不存在"));
        BookCopy copy = copies.findByIdForUpdate(reference.copyId())
                .orElseThrow(() -> new IllegalArgumentException("借阅关联的副本不存在"));
        if (!copy.getBookId().equals(book.getId()) || copy.getStatus() != BookCopyStatus.ON_LOAN) {
            throw new IllegalArgumentException("借阅记录与馆藏状态不一致，无法续借");
        }

        Loan loan = loans.findByIdForUpdate(reference.loanId())
                .orElseThrow(() -> new IllegalArgumentException("借阅记录不存在"));
        if (loan.getReturnedAt() != null) throw new IllegalArgumentException("已归还的借阅不能续借");
        if (!loan.getReaderId().equals(reader.getId()) || !loan.getCopyId().equals(copy.getId())) {
            throw new IllegalArgumentException("借阅记录关联资料已发生变化，请重新核对");
        }
        if (loan.getDueDate().isBefore(rules.today())) {
            throw new IllegalArgumentException("已逾期的借阅不能续借");
        }
        if (loans.existsByReader_IdAndReturnedAtIsNullAndDueDateBefore(reader.getId(), rules.today())) {
            throw new IllegalArgumentException("存在其他逾期未还借阅，不能续借");
        }
        if (loan.getRenewCount() >= 1) throw new IllegalArgumentException("每条借阅记录最多续借一次");

        reservationGuard.assertNoActiveReservation(reader.getId(), book.getId());
        loan.renew(RENEWAL_EXTENSION_DAYS);
        loans.saveAndFlush(loan);
        auditLogs.recordCurrent(authenticatedReaderUserId, UserRole.READER, "LOAN_RENEWED", "LOAN",
                loan.getId(), "读者续借14天");
        return loan;
    }
}
