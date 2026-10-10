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
import com.example.library.loan.Loan;
import com.example.library.loan.LoanRepository;
import com.example.library.loan.LoanRules;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
@Profile("test")
@ConditionalOnProperty(prefix = "library.reservations", name = "enabled", havingValue = "true")
public class ReservationService {

    private static final Duration HOLD_DURATION = Duration.ofHours(72);
    private static final List<ReservationStatus> ACTIVE_STATUSES = List.of(
            ReservationStatus.PENDING, ReservationStatus.READY);

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
        if (reservations.existsByReaderIdAndBookIdAndStatusIn(reader.getId(), bookId, ACTIVE_STATUSES)) {
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

        ReservationReference observed = reservations.findActiveReferenceById(reservationId, ACTIVE_STATUSES)
                .orElseThrow(() -> new IllegalArgumentException("有效预约不存在"));
        if (!authenticatedReaderUserId.equals(observed.readerUserId())) {
            throw new AccessDeniedException("不能取消他人的预约");
        }

        AppUser account = lockActiveReaderAccount(authenticatedReaderUserId);
        ReaderProfile reader = readers.findByIdForUpdate(observed.readerId())
                .orElseThrow(() -> new IllegalArgumentException("预约关联的读者档案不存在"));
        if (!reader.getUserId().equals(account.getId())) {
            throw new IllegalArgumentException("预约关联的读者资料已发生变化");
        }
        books.findByIdForUpdate(observed.bookId())
                .orElseThrow(() -> new IllegalArgumentException("预约关联的书目不存在"));

        ReservationReference current = reservations.findActiveReferenceById(reservationId, ACTIVE_STATUSES)
                .orElseThrow(() -> new IllegalArgumentException("预约状态已变化，请刷新后重试"));
        BookCopy heldCopy = null;
        if (current.status() == ReservationStatus.READY) {
            heldCopy = copies.findByIdForUpdate(current.copyId())
                    .orElseThrow(() -> new IllegalArgumentException("预约保留的副本不存在"));
            if (!heldCopy.getBookId().equals(current.bookId()) || heldCopy.getStatus() != BookCopyStatus.HELD) {
                throw new IllegalArgumentException("预约与保留副本状态不一致");
            }
        }

        // A READY cancellation can immediately hand the same copy to the next reader. Lock the cancelled
        // row plus the disabled FIFO prefix and next enabled row together, ordered by primary key.
        List<ReservationQueueCandidate> queue = heldCopy == null ? List.of()
                : reservations.findQueueCandidatesByBookId(current.bookId(), ReservationStatus.PENDING);
        List<Long> idsToLock = new ArrayList<>();
        idsToLock.add(reservationId);
        if (heldCopy != null) idsToLock.addAll(idsNeededForAllocation(queue, 1));
        List<Reservation> lockedQueue = reservations.findAllByIdInForUpdateOrderByIdAsc(
                idsToLock.stream().distinct().sorted().toList());
        Reservation reservation = lockedQueue.stream().filter(row -> row.getId().equals(reservationId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("预约状态已变化，请刷新后重试"));
        if (!reservation.getReaderId().equals(reader.getId()) || !reservation.getBookId().equals(current.bookId())
                || (reservation.getStatus() != ReservationStatus.PENDING
                && reservation.getStatus() != ReservationStatus.READY)) {
            throw new IllegalArgumentException("预约状态已变化，请刷新后重试");
        }

        reservation.cancel(circulationClock.instant());
        reservations.saveAndFlush(reservation);
        auditLogs.recordCurrent(account.getId(), UserRole.READER, "RESERVATION_CANCELLED", "RESERVATION",
                reservation.getId(), "取消图书预约");
        if (heldCopy != null) {
            heldCopy.releaseReservationHold();
            reallocateLockedCopies(current.bookId(), List.of(heldCopy), lockedQueue, queue);
        }
        return reservation;
    }

    /** Called only while the caller holds the book row and this available copy row. */
    @Transactional
    public Reservation allocateAvailableCopy(Long bookId, BookCopy copy) {
        if (bookId == null || copy == null || !bookId.equals(copy.getBookId())) {
            throw new IllegalArgumentException("预约分配的书目或副本无效");
        }
        if (copy.getStatus() != BookCopyStatus.AVAILABLE) return null;

        List<ReservationQueueCandidate> queue = reservations.findQueueCandidatesByBookId(
                bookId, ReservationStatus.PENDING);
        List<Long> idsToLock = idsNeededForAllocation(queue, 1);
        if (idsToLock.isEmpty()) return null;

        List<Reservation> lockedQueue = reservations.findAllByIdInForUpdateOrderByIdAsc(
                idsToLock.stream().distinct().sorted().toList());
        Map<Long, Reservation> byId = new HashMap<>();
        lockedQueue.forEach(row -> byId.put(row.getId(), row));
        Instant now = circulationClock.instant();
        for (ReservationQueueCandidate candidate : queue) {
            if (!idsToLock.contains(candidate.reservationId())) break;
            Reservation row = byId.get(candidate.reservationId());
            if (row == null || row.getStatus() != ReservationStatus.PENDING) continue;
            if (candidate.readerStatus() != UserStatus.ACTIVE || candidate.readerRole() != UserRole.READER) {
                row.cancel(now);
                reservations.save(row);
                auditLogs.recordCurrent(null, "RESERVATION_CANCELLED", "RESERVATION", row.getId(),
                        "跳过已停用读者的预约");
                continue;
            }
            row.assignCopy(copy.getId(), now.plus(HOLD_DURATION));
            copy.holdForReservation();
            reservations.saveAndFlush(row);
            copies.saveAndFlush(copy);
            auditLogs.recordCurrent(null, "RESERVATION_READY", "RESERVATION", row.getId(), "到书保留72小时");
            return row;
        }
        reservations.flush();
        return null;
    }

    @Transactional
    public void cancelForDisabledReader(Long readerProfileId, UserRole actorRole) {
        if (readerProfileId == null) throw new IllegalArgumentException("读者档案编号无效");
        List<Long> bookIds = reservations.findActiveBookIdsByReader(readerProfileId, ACTIVE_STATUSES);
        for (Long bookId : bookIds) {
            books.findByIdForUpdate(bookId).orElseThrow(() -> new IllegalArgumentException("预约书目不存在"));
            List<ReservationReference> readerRows = reservations.findActiveReferencesByReaderAndBook(
                    readerProfileId, bookId, ACTIVE_STATUSES);
            List<Long> copyIds = readerRows.stream().filter(row -> row.status() == ReservationStatus.READY)
                    .map(ReservationReference::copyId).distinct().sorted().toList();
            List<BookCopy> releasedCopies = copyIds.isEmpty()
                    ? List.of() : copies.findAllByIdInForUpdateOrderByIdAsc(copyIds);
            for (BookCopy copy : releasedCopies) {
                if (!copy.getBookId().equals(bookId) || copy.getStatus() != BookCopyStatus.HELD) {
                    throw new IllegalArgumentException("读者预约与保留副本状态不一致");
                }
            }

            List<ReservationQueueCandidate> queue = reservations.findQueueCandidatesByBookId(
                    bookId, ReservationStatus.PENDING);
            List<Long> idsToLock = new ArrayList<>();
            readerRows.forEach(row -> idsToLock.add(row.reservationId()));
            idsToLock.addAll(idsNeededForAllocation(queue, releasedCopies.size()));
            List<Reservation> lockedQueue = idsToLock.isEmpty() ? List.of()
                    : reservations.findAllByIdInForUpdateOrderByIdAsc(idsToLock.stream().distinct().sorted().toList());
            for (Reservation row : lockedQueue) {
                if (row.getReaderId().equals(readerProfileId)
                        && (row.getStatus() == ReservationStatus.PENDING || row.getStatus() == ReservationStatus.READY)) {
                    row.cancel(circulationClock.instant());
                    auditLogs.recordCurrent(actorRole, "RESERVATION_CANCELLED", "RESERVATION", row.getId(),
                            "停用读者账号并取消预约");
                }
            }
            for (BookCopy copy : releasedCopies) copy.releaseReservationHold();
            reservations.saveAllAndFlush(lockedQueue);
            copies.saveAllAndFlush(releasedCopies);
            if (!releasedCopies.isEmpty()) reallocateLockedCopies(bookId, releasedCopies, lockedQueue, queue);
        }
    }

    @Transactional
    public Loan fulfillForReader(Long operatorId, UserRole actorRole, Long reservationId, String readerNo) {
        requireStaff(actorRole);
        if (operatorId == null) throw new IllegalArgumentException("办理人账号无效");
        if (reservationId == null || reservationId < 1) throw new IllegalArgumentException("预约编号无效");
        String normalizedReaderNo = requiredReaderNo(readerNo);
        ReservationReference reference = reservations.findActiveReferenceById(reservationId, ACTIVE_STATUSES)
                .orElseThrow(() -> new IllegalArgumentException("有效预约不存在"));
        if (reference.status() != ReservationStatus.READY || reference.copyId() == null) {
            throw new IllegalArgumentException("该预约尚未分配到书副本");
        }

        Map<Long, AppUser> lockedUsers = lockUsersInOrder(operatorId, reference.readerUserId());
        AppUser operator = lockedUsers.get(operatorId);
        if (operator == null || operator.getStatus() != UserStatus.ACTIVE || !isStaff(operator.getRole())
                || operator.getRole() != actorRole) {
            throw new IllegalArgumentException("办理员工账号未启用或无权办理取书");
        }
        AppUser readerAccount = lockedUsers.get(reference.readerUserId());
        if (readerAccount == null || readerAccount.getRole() != UserRole.READER
                || readerAccount.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("读者账号未启用，不能办理取书");
        }

        ReaderProfile reader = readers.findByIdForUpdate(reference.readerId())
                .orElseThrow(() -> new IllegalArgumentException("预约关联的读者档案不存在"));
        if (!reader.getUserId().equals(readerAccount.getId()) || !reader.getReaderNo().equals(normalizedReaderNo)) {
            throw new IllegalArgumentException("读者证号与该预约不匹配");
        }
        Book book = books.findByIdForUpdate(reference.bookId())
                .orElseThrow(() -> new IllegalArgumentException("预约书目不存在"));
        if (book.getStatus() != BookStatus.ACTIVE) throw new IllegalArgumentException("已归档书目不能办理取书");
        ReservationReference current = reservations.findActiveReferenceById(reservationId, ACTIVE_STATUSES)
                .orElseThrow(() -> new IllegalArgumentException("预约状态已变化，请刷新后重试"));
        if (current.status() != ReservationStatus.READY || !current.readerId().equals(reader.getId())
                || !current.copyId().equals(reference.copyId())) {
            throw new IllegalArgumentException("预约状态已变化，请刷新后重试");
        }
        BookCopy copy = copies.findByIdForUpdate(current.copyId())
                .orElseThrow(() -> new IllegalArgumentException("预约保留的副本不存在"));
        if (!copy.getBookId().equals(book.getId()) || copy.getStatus() != BookCopyStatus.HELD) {
            throw new IllegalArgumentException("预约与保留副本状态不一致");
        }

        assertReaderEligible(reader.getId(), book.getId());
        Instant borrowedAt = circulationClock.instant();
        Loan loan = loans.saveAndFlush(Loan.fromReservationPickup(reader, copy, borrowedAt,
                loanRules.dueDateFor(borrowedAt), operator));
        copy.checkoutFromReservationHold();
        copies.saveAndFlush(copy);

        Reservation reservation = reservations.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("预约记录不存在"));
        if (reservation.getStatus() != ReservationStatus.READY
                || !reservation.getReaderId().equals(reader.getId())
                || !reservation.getBookId().equals(book.getId())
                || !reference.copyId().equals(reservation.getCopyId())) {
            throw new IllegalArgumentException("预约状态已变化，请刷新后重试");
        }
        reservation.fulfill(borrowedAt);
        reservations.saveAndFlush(reservation);
        auditLogs.recordCurrent(operatorId, actorRole, "LOAN_CHECKED_OUT", "LOAN", loan.getId(), "办理预约取书");
        auditLogs.recordCurrent(operatorId, actorRole, "RESERVATION_FULFILLED", "RESERVATION",
                reservation.getId(), "预约取书完成");
        return loan;
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
                    row.createdAt(), position, row.holdExpiresAt());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationStaffItem> listReadyForStaff(UserRole actorRole) {
        requireStaff(actorRole);
        return reservations.findReadyRowsForStaff(ReservationStatus.READY);
    }

    private void reallocateLockedCopies(Long bookId, List<BookCopy> availableCopies,
                                        List<Reservation> lockedActiveRows,
                                        List<ReservationQueueCandidate> queue) {
        if (availableCopies.isEmpty()) return;
        Map<Long, Reservation> reservationById = new HashMap<>();
        lockedActiveRows.forEach(row -> reservationById.put(row.getId(), row));
        List<BookCopy> copiesInOrder = availableCopies.stream()
                .sorted(Comparator.comparing(BookCopy::getId)).toList();
        Instant now = circulationClock.instant();
        for (BookCopy copy : copiesInOrder) {
            if (copy.getStatus() != BookCopyStatus.AVAILABLE) continue;
            for (ReservationQueueCandidate candidate : queue) {
                Reservation row = reservationById.get(candidate.reservationId());
                if (row == null || row.getStatus() != ReservationStatus.PENDING) continue;
                if (candidate.readerStatus() != UserStatus.ACTIVE || candidate.readerRole() != UserRole.READER) {
                    row.cancel(now);
                    auditLogs.recordCurrent(null, "RESERVATION_CANCELLED", "RESERVATION", row.getId(),
                            "跳过已停用读者的预约");
                    continue;
                }
                row.assignCopy(copy.getId(), now.plus(HOLD_DURATION));
                copy.holdForReservation();
                auditLogs.recordCurrent(null, "RESERVATION_READY", "RESERVATION", row.getId(), "到书保留72小时");
                break;
            }
        }
        reservations.saveAllAndFlush(lockedActiveRows);
        copies.saveAllAndFlush(copiesInOrder);
    }

    private static List<Long> idsNeededForAllocation(List<ReservationQueueCandidate> queue, int copyCount) {
        if (copyCount < 1) return List.of();
        List<Long> ids = new ArrayList<>();
        int eligibleReaders = 0;
        for (ReservationQueueCandidate candidate : queue) {
            ids.add(candidate.reservationId());
            if (candidate.readerStatus() == UserStatus.ACTIVE && candidate.readerRole() == UserRole.READER) {
                eligibleReaders++;
                if (eligibleReaders >= copyCount) break;
            }
        }
        return ids;
    }

    private void assertReaderEligible(Long readerId, Long bookId) {
        if (loans.existsByReader_IdAndReturnedAtIsNullAndDueDateBefore(readerId, loanRules.today())) {
            throw new IllegalArgumentException("读者有逾期未还图书，暂时不能办理取书；预约将保留至截止时间");
        }
        if (loans.countByReader_IdAndReturnedAtIsNull(readerId) >= loanRules.policy().maximumActiveLoans()) {
            throw new IllegalArgumentException("读者同时借阅数量已达到上限，预约将保留至截止时间");
        }
        if (loans.existsByReader_IdAndReturnedAtIsNullAndCopy_Book_Id(readerId, bookId)) {
            throw new IllegalArgumentException("读者已有同一书目的未还副本，预约将保留至截止时间");
        }
    }

    private AppUser lockActiveReaderAccount(Long readerUserId) {
        AppUser account = users.findByIdForUpdate(readerUserId)
                .orElseThrow(() -> new AccessDeniedException("读者账号不存在"));
        if (account.getRole() != UserRole.READER || account.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("读者账号未启用，不能办理预约");
        }
        return account;
    }

    private Map<Long, AppUser> lockUsersInOrder(Long operatorId, Long readerUserId) {
        List<Long> ids = Stream.of(operatorId, readerUserId).distinct().sorted().toList();
        Map<Long, AppUser> result = new HashMap<>();
        for (Long id : ids) users.findByIdForUpdate(id).ifPresent(user -> result.put(id, user));
        return result;
    }

    private static String requiredReaderNo(String readerNo) {
        if (readerNo == null || readerNo.trim().isEmpty() || readerNo.trim().length() > 50) {
            throw new IllegalArgumentException("读者证号无效");
        }
        return readerNo.trim();
    }

    private static boolean isStaff(UserRole role) {
        return role == UserRole.ADMIN || role == UserRole.LIBRARIAN;
    }

    private static void requireStaff(UserRole role) {
        if (!isStaff(role)) throw new AccessDeniedException("仅管理员或馆员可以办理取书");
    }
}
