package com.example.library.catalog;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {
    Optional<BookCopy> findByBarcode(String barcode);

    @Query("select new com.example.library.catalog.BookCopyReference(c.id, c.book.id) " +
            "from BookCopy c where c.barcode = :barcode")
    Optional<BookCopyReference> findReferenceByBarcode(@Param("barcode") String barcode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookCopy c where c.id = :id")
    Optional<BookCopy> findByIdForUpdate(@Param("id") Long id);

    @Query("select c from BookCopy c join fetch c.book where c.id = :id")
    Optional<BookCopy> findWithBookById(@Param("id") Long id);

    @Query("select c from BookCopy c join fetch c.book order by c.barcode")
    List<BookCopy> findAllWithBookOrderByBarcodeAsc();

    @Query("select c from BookCopy c join fetch c.book b where " +
            "lower(c.barcode) like lower(concat('%', :query, '%')) or " +
            "lower(b.title) like lower(concat('%', :query, '%')) order by c.barcode")
    List<BookCopy> findMatchingWithBook(@Param("query") String query);

    long countByBook_IdAndStatusNot(Long bookId, BookCopyStatus status);
    long countByBook_IdAndStatus(Long bookId, BookCopyStatus status);

    @Query("select new com.example.library.catalog.CatalogCopyView(c.shelfLocation, c.status) " +
            "from BookCopy c where c.book.id = :bookId and c.status <> :withdrawn " +
            "order by c.status, c.shelfLocation, c.id")
    List<CatalogCopyView> findCatalogCopies(@Param("bookId") Long bookId,
                                            @Param("withdrawn") BookCopyStatus withdrawn);

    @Query("select new com.example.library.catalog.BookCopySummary(c.book.id, c.status, count(c)) " +
            "from BookCopy c where c.book.id in :bookIds group by c.book.id, c.status")
    List<BookCopySummary> summarizeByBookIds(@Param("bookIds") Collection<Long> bookIds);
}
