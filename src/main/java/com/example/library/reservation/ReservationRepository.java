package com.example.library.reservation;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    @Query("select new com.example.library.reservation.ReservationReference(r.id, r.readerId, p.userId, " +
            "r.bookId, r.copyId, r.status, r.holdExpiresAt) from Reservation r join ReaderProfile p on p.id = r.readerId " +
            "where r.id = :id and r.status in :statuses")
    Optional<ReservationReference> findActiveReferenceById(@Param("id") Long id,
                                                            @Param("statuses") List<ReservationStatus> statuses);

    @Query("select new com.example.library.reservation.ReservationViewRow(r.id, r.bookId, b.title, r.status, " +
            "r.createdAt, r.holdExpiresAt) from Reservation r join Book b on b.id = r.bookId " +
            "where r.readerId = :readerId order by r.createdAt desc, r.id desc")
    List<ReservationViewRow> findRowsByReaderId(@Param("readerId") Long readerId);

    @Query("select count(r) from Reservation r where r.bookId = :bookId and r.status = :status and " +
            "(r.createdAt < :createdAt or (r.createdAt = :createdAt and r.id < :reservationId))")
    long countEarlierInQueue(@Param("bookId") Long bookId, @Param("status") ReservationStatus status,
                             @Param("createdAt") Instant createdAt,
                             @Param("reservationId") Long reservationId);

    List<Reservation> findByBookIdAndStatusOrderByCreatedAtAscIdAsc(Long bookId, ReservationStatus status);

    @Query("select distinct r.bookId from Reservation r where r.readerId = :readerId " +
            "and r.status in :statuses order by r.bookId asc")
    List<Long> findActiveBookIdsByReader(@Param("readerId") Long readerId,
                                         @Param("statuses") List<ReservationStatus> statuses);

    @Query("select new com.example.library.reservation.ReservationReference(r.id, r.readerId, p.userId, " +
            "r.bookId, r.copyId, r.status, r.holdExpiresAt) from Reservation r join ReaderProfile p on p.id = r.readerId " +
            "where r.readerId = :readerId and r.bookId = :bookId and r.status in :statuses order by r.id asc")
    List<ReservationReference> findActiveReferencesByReaderAndBook(@Param("readerId") Long readerId,
                                                                     @Param("bookId") Long bookId,
                                                                     @Param("statuses") List<ReservationStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id in :ids order by r.id asc")
    List<Reservation> findAllByIdInForUpdateOrderByIdAsc(@Param("ids") List<Long> ids);

    @Query("select new com.example.library.reservation.ReservationQueueCandidate(r.id, r.readerId, p.userId, " +
            "u.role, u.status, r.createdAt) from Reservation r join ReaderProfile p on p.id = r.readerId " +
            "join AppUser u on u.id = p.userId where r.bookId = :bookId and r.status = :status " +
            "order by r.createdAt asc, r.id asc")
    List<ReservationQueueCandidate> findQueueCandidatesByBookId(@Param("bookId") Long bookId,
                                                                 @Param("status") ReservationStatus status);

    @Query("select new com.example.library.reservation.ReservationStaffItem(r.id, p.id, p.readerNo, u.displayName, " +
            "b.title, r.createdAt, r.holdExpiresAt) from Reservation r join ReaderProfile p on p.id = r.readerId " +
            "join AppUser u on u.id = p.userId join Book b on b.id = r.bookId " +
            "where r.status = :status order by r.holdExpiresAt asc, r.id asc")
    List<ReservationStaffItem> findReadyRowsForStaff(@Param("status") ReservationStatus status);

    @Query("select r.id from Reservation r where r.status = :status and r.holdExpiresAt <= :now " +
            "order by r.bookId asc, r.id asc")
    List<Long> findExpiredReadyReservationIds(@Param("status") ReservationStatus status,
                                               @Param("now") Instant now);

    boolean existsByBookIdAndStatusIn(Long bookId, List<ReservationStatus> statuses);

    boolean existsByReaderIdAndBookIdAndStatusIn(Long readerId, Long bookId, List<ReservationStatus> statuses);
}
