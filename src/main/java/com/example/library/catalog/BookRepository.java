package com.example.library.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findByIsbn(String isbn);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);
    List<Book> findAllByOrderByTitleAsc();
    List<Book> findAllByStatusOrderByTitleAsc(BookStatus status);
    List<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrIsbnContainingIgnoreCaseOrderByTitleAsc(
            String title, String author, String isbn);

    @Query(value = "select b from Book b where b.status = :status " +
            "and (:categoryId is null or b.categoryId = :categoryId) " +
            "and (:keyword is null or LOCATE(LOWER(:keyword), LOWER(b.title)) > 0 " +
            "or LOCATE(LOWER(:keyword), LOWER(b.author)) > 0 " +
            "or LOCATE(LOWER(:keyword), LOWER(b.isbn)) > 0)",
            countQuery = "select count(b) from Book b where b.status = :status " +
                    "and (:categoryId is null or b.categoryId = :categoryId) " +
                    "and (:keyword is null or LOCATE(LOWER(:keyword), LOWER(b.title)) > 0 " +
                    "or LOCATE(LOWER(:keyword), LOWER(b.author)) > 0 " +
                    "or LOCATE(LOWER(:keyword), LOWER(b.isbn)) > 0)")
    Page<Book> searchCatalog(@Param("status") BookStatus status,
                             @Param("categoryId") Long categoryId,
                             @Param("keyword") String keyword,
                             Pageable pageable);

    Optional<Book> findByIdAndStatus(Long id, BookStatus status);
}
