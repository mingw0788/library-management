package com.example.library.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReaderProfileRepository extends JpaRepository<ReaderProfile, Long> {
    Optional<ReaderProfile> findByUserId(Long userId);
    @Query("select new com.example.library.account.ReaderProfileReference(r.id, r.userId) " +
            "from ReaderProfile r where r.readerNo = :readerNo")
    Optional<ReaderProfileReference> findReferenceByReaderNo(@Param("readerNo") String readerNo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReaderProfile r where r.id = :id")
    Optional<ReaderProfile> findByIdForUpdate(@Param("id") Long id);
    Optional<ReaderProfile> findByReaderNo(String readerNo);
    boolean existsByReaderNo(String readerNo);
    List<ReaderProfile> findAllByOrderByReaderNoAsc();
}
