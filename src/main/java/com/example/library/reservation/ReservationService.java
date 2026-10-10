package com.example.library.reservation;

import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.ReaderProfile;
import com.example.library.account.ReaderProfileRepository;
import com.example.library.account.UserRole;
import com.example.library.account.UserStatus;
import com.example.library.audit.AuditLogService;
import com.example.library.catalog.Book;
import com.example.library.catalog.BookCopy;
import com.example.library.catalog.BookCopyRepository;
import com.example.library.catalog.BookCopyStatus;
import com.example.library.catalog.BookRepository;
import com.example.library.catalog.BookStatus;
import com.example.library.loan.LoanRepository;
import com.example.library.loan.LoanRules;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@Profile("test")
@ConditionalOnProperty(prefix = "library.reservations", name = "enabled", havingValue = "true")
public class ReservationService {

    private final AppUserRepository users;
    private final ReaderProfileRepository readers;
    private final BookRepository books;
    private final BookCopyRepository copies;
    private final LoanRepository loans;
    private final ReservationRepository reservations;
    private final LoanRules loanRules;
    private final Clock circulationClock;
    private final AuditLogService auditLogs;

    public ReservationService(AppUserRepository users, ReaderProfileRepository readers, BookRepository books,
                              BookCopyRepository copies, LoanRepository loans, ReservationRepository reservations,
                              LoanRules loanRules, Clock circulationClock, AuditLogService auditLogs) {
        this.users = users;
        this.readers = readers;
        this.books = books;
        this.copies = copies;
        this.loans = loans;
        this.reservations = reservations;
        this.loanRules = loanRules;
        this.circulationClock = circulationClock;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public Reservation createForReader(Long authenticatedReaderUserId, Long bookId) {
        if (authenticatedReaderUserId == null) throw new AccessDeniedException("需要有效的读者身份");
        if (bookId == null || bookId < 1) throw new IllegalArgumentException("书目编号无效");

        AppUser account = lockActiveReaderAccount(authenticatedReaderUserId);
        ReaderProfile reader = readers.findByUserIdForUpdate(account.getId())
                .orElseThrow(() -> new IllegalArgumentException("读者档案不存在"));
        if (!reader.getUserId().equals(account.getId())) {
            throw new IllegalArgumentException("读者账号与档案关联异常");
        }

        Book book = books.findByIdForUpdate(bookId)
                .orElseThrow(() -> new IllegalArgumentException("书目不存在"));
        if (book.getStatus() != BookStatus.ACTIVE) {
            throw new IllegalArgumentException("已归档书目不能预约");
        }

        // Lock circulating copies in ID order so availability and circulation counts cannot change mid-check.
        List<BookCopy> circulatingCopies = copies.findCirculatingByBookIdForUpdateOrderByIdAsc(
                bookId, BookCopyStatus.WITHDRAWN);
        if (circulatingCopies.isEmpty()) {
            throw new IllegalArgumentException("该书目没有未注销副本，不能预约");
        }
        if (circulatingCopies.stream().anyMatch(copy -> copy.getStatus() == BookCopyStatus.AVAILABLE)) {
            throw new IllegalArgumentException("该书目当前有可借副本，不能预约");
        }

        if (loans.existsByReader_IdAndReturnedAtIsNullAndDueDateBefore(reader.getId(), loanRules.today())) {
            throw new IllegalArgumentException("存在逾期未还借阅，不能预约");
        }
        if (loans.countByReader_IdAndReturnedAtIsNull(reader.getId())
                >= loanRules.policy().maximumActiveLoans()) {
            throw new IllegalArgumentException("当前借阅数量已达到上限，不能预约");
        }
        if (loans.existsByReader_IdAndReturnedAtIsNullAndCopy_Book_Id(reader.getId(), bookId)) {
            throw new IllegalArgumentException("已有该书目的未还借阅，不能预约");
        }
        if (reservations.existsByReaderIdAndBookIdAndStatus(reader.getId(), bookId, ReservationStatus.PENDING)) {
            throw new IllegalArgumentException("已存在该书目的有效预约");
        }

        Reservation reservation = reservations.saveAndFlush(
                new Reservation(reader.getId(), bookId, circulationClock.instant()));
        auditLogs.recordCurrent(account.getId(), UserRole.READER, "RESERVATION_CREATED", "RESERVATION",
                reservation.getId(), "提交图书预约");
        return reservation;
    }

    @Transactional
    public Reservation cancelForReader(Long authenticatedReaderUserId, Long reservationId) {
        if (authenticatedReaderUserId == null) throw new AccessDeniedException("需要有效的读者身份");
        if (reservationId == null || reservationId < 1) throw new IllegalArgumentException("预约编号无效");

        ReservationReference reference = reservations
                .findReferenceByIdAndStatus(reservationId, ReservationStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("有效预约不存在"));
        if (!authenticatedReaderUserId.equals(reference.readerUserId())) {
            throw new AccessDeniedException("不能取消他人的预约");
        }

        AppUser account = lockActiveReaderAccount(authenticatedReaderUserId);
        ReaderProfile reader = readers.findByIdForUpdate(reference.readerId())
                .orElseThrow(() -> new IllegalArgumentException("预约关联的读者档案不存在"));
        if (!reader.getUserId().equals(account.getId()) || !reader.getId().equals(reference.readerId())) {
            throw new IllegalArgumentException("预约关联的读者资料已发生变化");
        }
        books.findByIdForUpdate(reference.bookId())
                .orElseThrow(() -> new IllegalArgumentException("预约关联的书目不存在"));

        Reservation reservation = reservations.findByIdForUpdate(reference.reservationId())
                .orElseThrow(() -> new IllegalArgumentException("预约记录不存在"));
        if (reservation.getStatus() != ReservationStatus.PENDING
                || !reservation.getReaderId().equals(reader.getId())
                || !reservation.getBookId().equals(reference.bookId())) {
            throw new IllegalArgumentException("预约状态已变化，请刷新后重试");
        }
        reservation.cancel(circulationClock.instant());
        reservations.saveAndFlush(reservation);
        auditLogs.recordCurrent(account.getId(), UserRole.READER, "RESERVATION_CANCELLED", "RESERVATION",
                reservation.getId(), "取消图书预约");
        return reservation;
    }

    @Transactional(readOnly = true)
    public List<ReservationListItem> listMine(Long authenticatedReaderUserId) {
        if (authenticatedReaderUserId == null) throw new AccessDeniedException("需要有效的读者身份");
        AppUser account = users.findById(authenticatedReaderUserId)
                .filter(user -> user.getRole() == UserRole.READER && user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("当前账号不是有效读者"));
        ReaderProfile reader = readers.findByUserId(account.getId())
                .orElseThrow(() -> new AccessDeniedException("当前读者档案不存在"));

        return reservations.findRowsByReaderId(reader.getId()).stream().map(row -> {
            Long position = row.status() == ReservationStatus.PENDING
                    ? reservations.countEarlierInQueue(row.bookId(), ReservationStatus.PENDING,
                    row.createdAt(), row.reservationId()) + 1
                    : null;
            return new ReservationListItem(row.reservationId(), row.bookId(), row.bookTitle(), row.status(),
                    row.createdAt(), position);
        }).toList();
    }

    private AppUser lockActiveReaderAccount(Long readerUserId) {
        AppUser account = users.findByIdForUpdate(readerUserId)
                .orElseThrow(() -> new AccessDeniedException("读者账号不存在"));
        if (account.getRole() != UserRole.READER || account.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("读者账号未启用，不能办理预约");
        }
        return account;
    }
}
