package com.example.library.catalog;

import com.example.library.loan.LoanRepository;
import org.springframework.stereotype.Component;

@Component
public class DatabaseBookArchiveGuard implements BookArchiveGuard {

    private final BookCopyRepository copies;
    private final LoanRepository loans;

    public DatabaseBookArchiveGuard(BookCopyRepository copies, LoanRepository loans) {
        this.copies = copies;
        this.loans = loans;
    }

    @Override
    public void assertCanArchive(Long bookId) {
        if (copies.countByBook_IdAndStatusNot(bookId, BookCopyStatus.WITHDRAWN) > 0) {
            throw new IllegalArgumentException("书目仍有未注销副本，不能归档");
        }
        if (loans.existsByCopy_Book_IdAndReturnedAtIsNull(bookId)) {
            throw new IllegalArgumentException("书目仍有未归还借阅，不能归档");
        }
    }
}
