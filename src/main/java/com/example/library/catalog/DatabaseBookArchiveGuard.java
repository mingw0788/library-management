package com.example.library.catalog;

import com.example.library.loan.LoanRepository;
import com.example.library.reservation.ReservationRepository;
import com.example.library.reservation.ReservationStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseBookArchiveGuard implements BookArchiveGuard {

    private final BookCopyRepository copies;
    private final LoanRepository loans;
    private final ReservationRepository reservations;

    public DatabaseBookArchiveGuard(BookCopyRepository copies, LoanRepository loans,
                                    ReservationRepository reservations) {
        this.copies = copies;
        this.loans = loans;
        this.reservations = reservations;
    }

    @Override
    public void assertCanArchive(Long bookId) {
        if (reservations.existsByBookIdAndStatusIn(bookId,
                List.of(ReservationStatus.PENDING, ReservationStatus.READY))) {
            throw new IllegalArgumentException("书目仍有活动预约，不能归档");
        }
        if (copies.countByBook_IdAndStatusNot(bookId, BookCopyStatus.WITHDRAWN) > 0) {
            throw new IllegalArgumentException("书目仍有未注销副本，不能归档");
        }
        if (loans.existsByCopy_Book_IdAndReturnedAtIsNull(bookId)) {
            throw new IllegalArgumentException("书目仍有未归还借阅，不能归档");
        }
    }
}
