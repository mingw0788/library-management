package com.example.library.catalog;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE_NUMBER = 100_000;

    private final BookRepository books;
    private final CategoryRepository categories;
    private final BookCopyRepository copies;

    public CatalogService(BookRepository books, CategoryRepository categories, BookCopyRepository copies) {
        this.books = books;
        this.categories = categories;
        this.copies = copies;
    }

    @Transactional(readOnly = true)
    public CatalogSearchResult search(String keyword, Long categoryId, int requestedPage, int requestedSize,
                                      String requestedSort) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (categoryId != null && !categories.existsById(categoryId)) {
            throw new IllegalArgumentException("所选分类不存在，请重新选择");
        }
        if (requestedPage < 0 || requestedPage > MAX_PAGE_NUMBER) {
            throw new IllegalArgumentException("页码必须在 0—100000 之间");
        }
        if (requestedSize < 1) throw new IllegalArgumentException("每页数量至少为 1");
        int pageSize = Math.min(requestedSize, MAX_PAGE_SIZE);
        String normalizedSort = normalizeSort(requestedSort);
        Pageable pageable = PageRequest.of(requestedPage, pageSize, sortFor(normalizedSort));
        Page<Book> result = books.searchCatalog(BookStatus.ACTIVE, categoryId, normalizedKeyword, pageable);
        if (result.getTotalPages() > 0 && requestedPage >= result.getTotalPages()) {
            pageable = PageRequest.of(result.getTotalPages() - 1, pageSize, sortFor(normalizedSort));
            result = books.searchCatalog(BookStatus.ACTIVE, categoryId, normalizedKeyword, pageable);
        }
        List<Category> categoryOptions = categories.findAllByOrderByNameAsc();
        Map<Long, String> categoryNames = new HashMap<>();
        categoryOptions.forEach(category -> categoryNames.put(category.getId(), category.getName()));
        return new CatalogSearchResult(result, categoryOptions,
                inventoryFor(result.getContent()), Map.copyOf(categoryNames),
                normalizedKeyword, categoryId, normalizedSort);
    }

    @Transactional(readOnly = true)
    public CatalogBookDetail detail(Long id) {
        if (id == null || id < 1) throw new IllegalArgumentException("书目不存在");
        Book book = books.findByIdAndStatus(id, BookStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("书目不存在或已归档"));
        Category category = categories.findById(book.getCategoryId())
                .orElseThrow(() -> new IllegalStateException("书目分类资料不完整"));
        return new CatalogBookDetail(book, category,
                copies.findCatalogCopies(id, BookCopyStatus.WITHDRAWN),
                copies.countByBook_IdAndStatus(id, BookCopyStatus.AVAILABLE));
    }

    @Transactional(readOnly = true)
    public List<Category> categories() {
        return categories.findAllByOrderByNameAsc();
    }

    private Map<Long, BookCopyInventory> inventoryFor(List<Book> pageBooks) {
        if (pageBooks.isEmpty()) return Map.of();
        List<Long> ids = pageBooks.stream().map(Book::getId).toList();
        Map<Long, long[]> values = new HashMap<>();
        ids.forEach(id -> values.put(id, new long[4]));
        for (BookCopySummary summary : copies.summarizeByBookIds(ids)) {
            long[] counts = values.get(summary.bookId());
            int index = switch (summary.status()) {
                case AVAILABLE -> 0;
                case ON_LOAN -> 1;
                case HELD -> 2;
                case WITHDRAWN -> 3;
            };
            counts[index] = summary.quantity();
        }
        Map<Long, BookCopyInventory> result = new HashMap<>();
        values.forEach((id, count) -> result.put(id,
                new BookCopyInventory(count[0], count[1], count[2], count[3])));
        return Map.copyOf(result);
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return null;
        String normalized = keyword.trim();
        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw new IllegalArgumentException("检索词不能超过 100 个字符");
        }
        return normalized;
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "titleAsc";
        return switch (sort) {
            case "titleAsc", "titleDesc", "authorAsc", "authorDesc", "dateAsc", "dateDesc" -> sort;
            default -> throw new IllegalArgumentException("排序方式无效，请重新选择");
        };
    }

    private static Sort sortFor(String sort) {
        String property = switch (sort) {
            case "titleAsc", "titleDesc" -> "title";
            case "authorAsc", "authorDesc" -> "author";
            case "dateAsc", "dateDesc" -> "publishedDate";
            default -> throw new IllegalArgumentException("排序方式无效，请重新选择");
        };
        Sort.Direction direction = sort.endsWith("Desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort stableSort = Sort.by(new Sort.Order(direction, property));
        if (!property.equals("title")) stableSort = stableSort.and(Sort.by(Sort.Order.asc("title")));
        return stableSort.and(Sort.by(Sort.Order.asc("id")));
    }
}
