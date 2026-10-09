package com.example.library.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReaderProfileRepository extends JpaRepository<ReaderProfile, Long> {
    Optional<ReaderProfile> findByUserId(Long userId);
    Optional<ReaderProfile> findByReaderNo(String readerNo);
    boolean existsByReaderNo(String readerNo);
    List<ReaderProfile> findAllByOrderByReaderNoAsc();
}
