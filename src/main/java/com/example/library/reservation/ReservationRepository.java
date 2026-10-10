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

    @Query("select new com.example.library.reservation.ReservationReference(r.id, r.readerId, p.userId, r.bookId) " +
            "from Reservation r join ReaderProfile p on p.id = r.readerId " +
            "where r.id = :id and r.status = :status")
    Optional<ReservationReference> findReferenceByIdAndStatus(@Param("id") Long id,
                                                                @Param("status") ReservationStatus status);

    boolean existsByReaderIdAndBookIdAndStatus(Long readerId, Long bookId, ReservationStatus status);

    @Query("select new com.example.library.reservation.ReservationViewRow(r.id, r.bookId, b.title, r.status, " +
            "r.createdAt) from Reservation r join Book b on b.id = r.bookId " +
            "where r.readerId = :readerId order by r.createdAt desc, r.id desc")
    List<ReservationViewRow> findRowsByReaderId(@Param("readerId") Long readerId);

    @Query("select count(r) from Reservation r where r.bookId = :bookId and r.status = :status and " +
            "(r.createdAt < :createdAt or (r.createdAt = :createdAt and r.id < :reservationId))")
    long countEarlierInQueue(@Param("bookId") Long bookId, @Param("status") ReservationStatus status,
                             @Param("createdAt") Instant createdAt,
                             @Param("reservationId") Long reservationId);

    List<Reservation> findByBookIdAndStatusOrderByCreatedAtAscIdAsc(Long bookId, ReservationStatus status);
}
