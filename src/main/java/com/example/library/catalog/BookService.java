package com.example.library.catalog;

import com.example.library.account.UserRole;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BookService {

    private final BookRepository books;
    private final CategoryRepository categories;
    private final BookArchiveGuard archiveGuard;

    public BookService(BookRepository books, CategoryRepository categories, BookArchiveGuard archiveGuard) {
        this.books = books;
        this.categories = categories;
        this.archiveGuard = archiveGuard;
    }

    @Transactional(readOnly = true)
    public List<Book> search(UserRole actorRole, String query) {
        requireStaff(actorRole);
        if (query == null || query.trim().isEmpty()) return books.findAllByOrderByTitleAsc();
        String normalized = query.trim();
        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw new IllegalArgumentException("查询词不能超过 100 个字符");
        }
        return books.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCaseOrIsbnContainingIgnoreCaseOrderByTitleAsc(
                normalized, normalized, normalized);
    }

    @Transactional(readOnly = true)
    public List<Book> listActiveBooksForCatalog() {
        return books.findAllByStatusOrderByTitleAsc(BookStatus.ACTIVE);
    }

    @Transactional
    public Book create(UserRole actorRole, String title, String author, String publisher,
                       LocalDate publishedDate, String isbn, Long categoryId, String description) {
        requireStaff(actorRole);
        BookDetails details = validateDetails(title, author, publisher, publishedDate, isbn, categoryId, description, null);
        return books.saveAndFlush(new Book(details.categoryId(), details.title(), details.author(), details.publisher(),
                details.publishedDate(), details.isbn(), details.description()));
    }

    @Transactional
    public Book update(UserRole actorRole, Long id, long expectedVersion, String title, String author,
                       String publisher, LocalDate publishedDate, String isbn, Long categoryId, String description) {
        requireStaff(actorRole);
        Book book = requireBook(id);
        if (book.getVersion() != expectedVersion) {
            throw new ObjectOptimisticLockingFailureException(Book.class, id);
        }
        if (book.getStatus() != BookStatus.ACTIVE) throw new IllegalArgumentException("已归档书目不能修改");
        BookDetails details = validateDetails(title, author, publisher, publishedDate, isbn, categoryId, description, id);
        book.update(details.categoryId(), details.title(), details.author(), details.publisher(),
                details.publishedDate(), details.isbn(), details.description());
        return books.saveAndFlush(book);
    }

    @Transactional
    public void archive(UserRole actorRole, Long id) {
        requireStaff(actorRole);
        if (id == null) throw new IllegalArgumentException("书目编号不能为空");
        Book book = books.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("书目不存在"));
        archiveGuard.assertCanArchive(id);
        book.archive();
        books.saveAndFlush(book);
    }

    private BookDetails validateDetails(String title, String author, String publisher, LocalDate publishedDate,
                                        String isbn, Long categoryId, String description, Long currentBookId) {
        String normalizedTitle = requiredText(title, "书名", 200);
        String normalizedAuthor = requiredText(author, "作者", 200);
        String normalizedPublisher = optionalText(publisher, "出版社", 200);
        String normalizedDescription = optionalText(description, "简介", 2000);
        if (categoryId == null || !categories.existsById(categoryId)) {
            throw new IllegalArgumentException("请选择有效的图书分类");
        }
        String normalizedIsbn = IsbnNormalizer.normalize(isbn);
        ensureIsbnAvailable(normalizedIsbn, currentBookId);
        return new BookDetails(normalizedTitle, normalizedAuthor, normalizedPublisher,
                publishedDate, normalizedIsbn, categoryId, normalizedDescription);
    }

    private void ensureIsbnAvailable(String isbn, Long currentBookId) {
        if (isbn == null) return;
        books.findByIsbn(isbn).filter(existing -> currentBookId == null || !existing.getId().equals(currentBookId))
                .ifPresent(existing -> { throw new IllegalArgumentException("ISBN 已被其他书目使用"); });
    }

    private Book requireBook(Long id) {
        if (id == null) throw new IllegalArgumentException("书目编号不能为空");
        return books.findById(id).orElseThrow(() -> new IllegalArgumentException("书目不存在"));
    }

    private static String requiredText(String value, String label, int maxCodePoints) {
        if (value == null) throw new IllegalArgumentException(label + "不能为空");
        String normalized = value.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > maxCodePoints) {
            throw new IllegalArgumentException(label + "长度须为 1—" + maxCodePoints + " 个字符");
        }
        return normalized;
    }

    private static String optionalText(String value, String label, int maxCodePoints) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new IllegalArgumentException(label + "不能超过 " + maxCodePoints + " 个字符");
        }
        return normalized;
    }

    private static void requireStaff(UserRole role) {
        if (role != UserRole.ADMIN && role != UserRole.LIBRARIAN) {
            throw new AccessDeniedException("仅管理员或馆员可以管理书目");
        }
    }

    private record BookDetails(String title, String author, String publisher, LocalDate publishedDate,
                               String isbn, Long categoryId, String description) {}
}
