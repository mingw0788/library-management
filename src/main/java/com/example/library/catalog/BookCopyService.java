package com.example.library.catalog;

import com.example.library.account.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BookCopyService {

    private final BookRepository books;
    private final BookCopyRepository copies;

    public BookCopyService(BookRepository books, BookCopyRepository copies) {
        this.books = books;
        this.copies = copies;
    }

    @Transactional(readOnly = true)
    public List<BookCopy> search(UserRole actorRole, String query) {
        requireStaff(actorRole);
        if (query == null || query.trim().isEmpty()) return copies.findAllWithBookOrderByBarcodeAsc();
        String normalized = query.trim();
        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw new IllegalArgumentException("查询词不能超过 100 个字符");
        }
        return copies.findMatchingWithBook(normalized);
    }

    @Transactional
    public BookCopy register(UserRole actorRole, Long bookId, String barcode, String shelfLocation) {
        requireStaff(actorRole);
        if (bookId == null) throw new IllegalArgumentException("请选择有效的书目");
        Book book = books.findByIdForUpdate(bookId).orElseThrow(() -> new IllegalArgumentException("书目不存在"));
        if (book.getStatus() != BookStatus.ACTIVE) {
            throw new IllegalArgumentException("已归档书目不能登记副本");
        }
        String normalizedBarcode = requiredText(barcode, "馆藏条码", 50);
        String normalizedLocation = optionalText(shelfLocation, "馆藏位置", 100);
        if (copies.findByBarcode(normalizedBarcode).isPresent()) {
            throw new IllegalArgumentException("馆藏条码已被使用");
        }
        return copies.saveAndFlush(new BookCopy(book, normalizedBarcode, normalizedLocation));
    }

    @Transactional
    public void withdraw(UserRole actorRole, Long id) {
        requireStaff(actorRole);
        if (id == null) throw new IllegalArgumentException("副本编号不能为空");
        BookCopy copy = copies.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("副本不存在"));
        copy.withdraw();
        copies.saveAndFlush(copy);
    }

    @Transactional(readOnly = true)
    public Map<Long, BookCopyInventory> inventoryByBookIds(Collection<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) return Map.of();
        Map<Long, long[]> totals = new HashMap<>();
        bookIds.forEach(id -> totals.put(id, new long[3]));
        for (BookCopySummary summary : copies.summarizeByBookIds(bookIds)) {
            long[] counts = totals.get(summary.bookId());
            int slot = switch (summary.status()) {
                case AVAILABLE -> 0;
                case ON_LOAN -> 1;
                case WITHDRAWN -> 2;
            };
            counts[slot] = summary.quantity();
        }
        Map<Long, BookCopyInventory> result = new HashMap<>();
        totals.forEach((id, counts) -> result.put(id,
                new BookCopyInventory(counts[0], counts[1], counts[2])));
        return Map.copyOf(result);
    }

    private static String requiredText(String value, String label, int maxCodePoints) {
        if (value == null) throw new IllegalArgumentException(label + "不能为空");
        String normalized = value.trim();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > maxCodePoints || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + "长度须为 1—" + maxCodePoints + " 个字符且不能包含控制字符");
        }
        return normalized;
    }

    private static String optionalText(String value, String label, int maxCodePoints) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + "不能超过 " + maxCodePoints + " 个字符或包含控制字符");
        }
        return normalized;
    }

    private static void requireStaff(UserRole role) {
        if (role != UserRole.ADMIN && role != UserRole.LIBRARIAN) {
            throw new AccessDeniedException("仅管理员或馆员可以管理实体副本");
        }
    }
}
