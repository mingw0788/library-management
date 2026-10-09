package com.example.library.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookRepository extends JpaRepository<Book, Long> {
    Optional<Book> findByIsbn(String isbn);
    List<Book> findAllByOrderByTitleAsc();
    List<Book> findAllByStatusOrderByTitleAsc(BookStatus status);
    List<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrIsbnContainingIgnoreCaseOrderByTitleAsc(
            String title, String author, String isbn);
}
