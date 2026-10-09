package com.example.library.catalog;

import org.springframework.stereotype.Component;

@Component
public class DatabaseBookArchiveGuard implements BookArchiveGuard {

    private final BookCopyRepository copies;

    public DatabaseBookArchiveGuard(BookCopyRepository copies) {
        this.copies = copies;
    }

    @Override
    public void assertCanArchive(Long bookId) {
        if (copies.countByBook_IdAndStatusNot(bookId, BookCopyStatus.WITHDRAWN) > 0) {
            throw new IllegalArgumentException("书目仍有未注销副本，不能归档");
        }
    }
}
