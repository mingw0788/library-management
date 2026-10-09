package com.example.library.dashboard;

import com.example.library.account.ReaderProfile;
import com.example.library.account.ReaderProfileRepository;
import com.example.library.account.UserRole;
import com.example.library.catalog.BookCopyRepository;
import com.example.library.catalog.BookCopyStatus;
import com.example.library.catalog.BookRepository;
import com.example.library.loan.LoanRepository;
import com.example.library.loan.LoanRules;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardStatisticsService {

    private final BookRepository books;
    private final BookCopyRepository copies;
    private final LoanRepository loans;
    private final ReaderProfileRepository readers;
    private final CurrentIdentityService currentIdentity;
    private final LoanRules loanRules;

    public DashboardStatisticsService(BookRepository books, BookCopyRepository copies, LoanRepository loans,
                                      ReaderProfileRepository readers, CurrentIdentityService currentIdentity,
                                      LoanRules loanRules) {
        this.books = books;
        this.copies = copies;
        this.loans = loans;
        this.readers = readers;
        this.currentIdentity = currentIdentity;
        this.loanRules = loanRules;
    }

    @Transactional(readOnly = true)
    public StaffDashboardStats staffStatistics() {
        requireStaff(currentIdentity.requireCurrentIdentity());
        var today = loanRules.today();
        return new StaffDashboardStats(
                books.count(),
                copies.count(),
                copies.countByStatusNot(BookCopyStatus.WITHDRAWN),
                copies.countByStatus(BookCopyStatus.AVAILABLE),
                loans.countByReturnedAtIsNull(),
                loans.countByReturnedAtIsNullAndDueDateBefore(today));
    }

    @Transactional(readOnly = true)
    public ReaderDashboardStats readerStatistics() {
        Long readerUserId = currentIdentity.requireReaderUserId();
        ReaderProfile reader = readers.findByUserId(readerUserId)
                .orElseThrow(() -> new AccessDeniedException("当前读者档案不存在"));
        var today = loanRules.today();
        return new ReaderDashboardStats(
                loans.countByReader_IdAndReturnedAtIsNull(reader.getId()),
                loans.countByReader_IdAndReturnedAtIsNullAndDueDateBefore(reader.getId(), today));
    }

    private static void requireStaff(CurrentIdentity identity) {
        if (identity.role() != UserRole.ADMIN && identity.role() != UserRole.LIBRARIAN) {
            throw new AccessDeniedException("仅管理员或馆员可以查看全馆统计");
        }
    }
}
