package com.example.library.loan;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> findByIdForUpdate(@Param("id") Long id);

    @Query("select new com.example.library.loan.LoanReference(l.id, l.reader.id, l.reader.userId, " +
            "l.copy.id, l.copy.book.id) from Loan l where l.id = :id and l.returnedAt is null")
    Optional<LoanReference> findActiveReferenceById(@Param("id") Long id);

    @Query("select new com.example.library.loan.LoanReference(l.id, l.reader.id, l.reader.userId, " +
            "l.copy.id, l.copy.book.id) from Loan l where l.copy.barcode = :barcode and l.returnedAt is null")
    Optional<LoanReference> findActiveReferenceByBarcode(@Param("barcode") String barcode);

    long countByReader_IdAndReturnedAtIsNull(Long readerId);

    Optional<Loan> findByReader_IdAndReturnedAtIsNull(Long readerId);

    boolean existsByReader_IdAndReturnedAtIsNullAndDueDateBefore(Long readerId, LocalDate date);

    boolean existsByReader_IdAndReturnedAtIsNullAndCopy_Book_Id(Long readerId, Long bookId);

    boolean existsByCopy_Book_IdAndReturnedAtIsNull(Long bookId);

    boolean existsByActiveCopyId(Long copyId);

    @Query(value = "select new com.example.library.loan.LoanViewRow(l.id, r.readerNo, readerAccount.displayName, " +
            "c.barcode, b.title, l.borrowedAt, l.dueDate, l.returnedAt, " +
            "checkoutOperator.displayName, returnOperator.displayName) " +
            "from Loan l join l.reader r join AppUser readerAccount on readerAccount.id = r.userId " +
            "join l.copy c join c.book b join l.checkoutOperator checkoutOperator " +
            "left join l.returnOperator returnOperator " +
            "where (:readerProfileId is null or r.id = :readerProfileId) " +
            "and (:readerQuery is null or lower(r.readerNo) = lower(:readerQuery) " +
            "or lower(readerAccount.displayName) like concat('%', lower(:readerQuery), '%')) " +
            "and (:barcodeQuery is null or lower(c.barcode) like concat('%', lower(:barcodeQuery), '%')) " +
            "and (:state = 'ALL' or (:state = 'CURRENT' and l.returnedAt is null) " +
            "or (:state = 'RETURNED' and l.returnedAt is not null)) " +
            "and (:fromInclusive is null or l.borrowedAt >= :fromInclusive) " +
            "and (:untilExclusive is null or l.borrowedAt < :untilExclusive)",
            countQuery = "select count(l) from Loan l join l.reader r " +
            "join AppUser readerAccount on readerAccount.id = r.userId join l.copy c " +
            "where (:readerProfileId is null or r.id = :readerProfileId) " +
            "and (:readerQuery is null or lower(r.readerNo) = lower(:readerQuery) " +
            "or lower(readerAccount.displayName) like concat('%', lower(:readerQuery), '%')) " +
            "and (:barcodeQuery is null or lower(c.barcode) like concat('%', lower(:barcodeQuery), '%')) " +
            "and (:state = 'ALL' or (:state = 'CURRENT' and l.returnedAt is null) " +
            "or (:state = 'RETURNED' and l.returnedAt is not null)) " +
            "and (:fromInclusive is null or l.borrowedAt >= :fromInclusive) " +
            "and (:untilExclusive is null or l.borrowedAt < :untilExclusive)")
    Page<LoanViewRow> search(@Param("readerProfileId") Long readerProfileId,
                             @Param("readerQuery") String readerQuery,
                             @Param("barcodeQuery") String barcodeQuery,
                             @Param("state") String state,
                             @Param("fromInclusive") Instant fromInclusive,
                             @Param("untilExclusive") Instant untilExclusive,
                             Pageable pageable);
}
