package com.example.library.loan;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> findByIdForUpdate(@Param("id") Long id);

    long countByReader_IdAndReturnedAtIsNull(Long readerId);

    Optional<Loan> findByReader_IdAndReturnedAtIsNull(Long readerId);

    boolean existsByReader_IdAndReturnedAtIsNullAndDueDateBefore(Long readerId, LocalDate date);

    boolean existsByReader_IdAndReturnedAtIsNullAndCopy_Book_Id(Long readerId, Long bookId);

    boolean existsByCopy_Book_IdAndReturnedAtIsNull(Long bookId);

    boolean existsByActiveCopyId(Long copyId);
}
