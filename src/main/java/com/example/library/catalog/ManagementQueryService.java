package com.example.library.catalog;

import com.example.library.account.ManagementReaderRow;
import com.example.library.account.UserRole;
import com.example.library.account.UserStatus;
import com.example.library.web.ManagementSearch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ManagementQueryService {
    public static final Set<String> READER_STATES = Set.of("ACTIVE", "DISABLED");
    public static final Set<String> BOOK_STATES = Set.of("ACTIVE", "ARCHIVED");
    public static final Set<String> COPY_STATES = Set.of("AVAILABLE", "ON_LOAN", "HELD", "WITHDRAWN");
    private final EntityManager entityManager;

    public ManagementQueryService(EntityManager entityManager) { this.entityManager = entityManager; }

    public Page<ManagementReaderRow> readers(UserRole role, ManagementSearch search) {
        requireStaff(role);
        String from = "from ReaderProfile r join AppUser u on u.id = r.userId "
                + "where u.role = com.example.library.account.UserRole.READER "
                + "and (:q = '' or locate(lower(:q), lower(r.readerNo)) > 0 "
                + "or locate(lower(:q), lower(u.displayName)) > 0 or locate(lower(:q), lower(u.loginName)) > 0)";
        Object status = search.status().equals("ALL") ? null : UserStatus.valueOf(search.status());
        if (status != null) from += " and u.status = :state";
        return page("select new com.example.library.account.ManagementReaderRow(u.id, r.readerNo, u.displayName, r.contact, cast(u.status as string)) ",
                from, " order by r.readerNo, r.id", ManagementReaderRow.class, search, status);
    }

    public Page<Book> books(UserRole role, ManagementSearch search) {
        requireStaff(role);
        String from = "from Book b where (:q = '' or locate(lower(:q), lower(b.title)) > 0 "
                + "or locate(lower(:q), lower(b.author)) > 0 or locate(lower(:q), lower(b.isbn)) > 0)";
        Object status = search.status().equals("ALL") ? null : BookStatus.valueOf(search.status());
        if (status != null) from += " and b.status = :state";
        return page("select b ", from, " order by b.title, b.id", Book.class, search, status);
    }

    public Page<BookCopy> copies(UserRole role, ManagementSearch search) {
        requireStaff(role);
        String from = "from BookCopy c join c.book b where (:q = '' or locate(lower(:q), lower(c.barcode)) > 0 "
                + "or locate(lower(:q), lower(b.title)) > 0)";
        Object status = search.status().equals("ALL") ? null : BookCopyStatus.valueOf(search.status());
        if (status != null) from += " and c.status = :state";
        return page("select c ", from, " order by c.barcode, c.id", BookCopy.class, search, status);
    }

    private <T> Page<T> page(String select, String from, String order, Class<T> type,
                              ManagementSearch search, Object status) {
        long count = bind(entityManager.createQuery("select count(*) " + from, Long.class), search, status).getSingleResult();
        int last = (int) Math.min(100_000L, Math.max(0L, (count - 1L) / ManagementSearch.PAGE_SIZE));
        int index = Math.min(search.page(), last);
        String contentFrom = type == BookCopy.class ? from.replace("join c.book b", "join fetch c.book b") : from;
        var rows = bind(entityManager.createQuery(select + contentFrom + order, type), search, status)
                .setFirstResult(index * ManagementSearch.PAGE_SIZE).setMaxResults(ManagementSearch.PAGE_SIZE).getResultList();
        return new PageImpl<>(rows, PageRequest.of(index, ManagementSearch.PAGE_SIZE), count);
    }

    private <T> TypedQuery<T> bind(TypedQuery<T> query, ManagementSearch search, Object status) {
        query.setParameter("q", search.query());
        if (status != null) query.setParameter("state", status);
        return query;
    }

    private static void requireStaff(UserRole role) {
        if (role != UserRole.ADMIN && role != UserRole.LIBRARIAN) throw new AccessDeniedException("仅工作人员可以管理列表");
    }
}
