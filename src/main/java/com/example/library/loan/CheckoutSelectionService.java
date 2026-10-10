package com.example.library.loan;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;

/** Read-only staff selection; the checkout transaction remains authoritative. */
@Service
@Transactional(readOnly = true)
public class CheckoutSelectionService {
    private final EntityManager entityManager;
    private final LoanEligibilityPolicy eligibility;

    public CheckoutSelectionService(EntityManager entityManager, LoanEligibilityPolicy eligibility) {
        this.entityManager = entityManager;
        this.eligibility = eligibility;
    }

    private static final String READERS = "select new com.example.library.loan.CheckoutReaderChoice(r.id, r.readerNo, u.displayName) "
            + "from ReaderProfile r join AppUser u on u.id = r.userId "
            + "where u.role = com.example.library.account.UserRole.READER "
            + "and u.status = com.example.library.account.UserStatus.ACTIVE ";
    private static final String COPIES = "select new com.example.library.loan.CheckoutCopyChoice(c.id, c.barcode, b.title, b.author, c.shelfLocation) "
            + "from BookCopy c join c.book b where b.status = com.example.library.catalog.BookStatus.ACTIVE "
            + "and c.status = com.example.library.catalog.BookCopyStatus.AVAILABLE ";

    public List<CheckoutReaderChoice> readers(String query) {
        String value = normalizeQuery(query);
        if (value.isEmpty()) return List.of();
        return entityManager.createQuery(READERS + "and (locate(lower(:q), lower(r.readerNo)) > 0 "
                        + "or locate(lower(:q), lower(u.displayName)) > 0) order by r.readerNo", CheckoutReaderChoice.class)
                .setParameter("q", value).setMaxResults(20).getResultList();
    }

    public List<CheckoutCopyChoice> copies(String query) {
        String value = normalizeQuery(query);
        if (value.isEmpty()) return List.of();
        return entityManager.createQuery(COPIES + "and (locate(lower(:q), lower(c.barcode)) > 0 "
                        + "or locate(lower(:q), lower(b.title)) > 0 or locate(lower(:q), lower(b.author)) > 0) "
                        + "order by b.title, c.barcode", CheckoutCopyChoice.class)
                .setParameter("q", value).setMaxResults(20).getResultList();
    }

    public CheckoutReaderChoice reader(String readerNo) {
        return entityManager.createQuery(READERS + "and r.readerNo = :value", CheckoutReaderChoice.class)
                .setParameter("value", normalizeIdentifier(readerNo)).getResultStream().findFirst().orElse(null);
    }

    public CheckoutCopyChoice copy(String barcode) {
        return entityManager.createQuery(COPIES + "and c.barcode = :value", CheckoutCopyChoice.class)
                .setParameter("value", normalizeIdentifier(barcode)).getResultStream().findFirst().orElse(null);
    }

    // The policy owns its read transaction; a rejected eligibility check must not
    // mark an enclosing preview transaction rollback-only before we display its message.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public String restriction(CheckoutReaderChoice reader, CheckoutCopyChoice copy) {
        try {
            eligibility.assertCanBorrow(reader.id(), copy.id());
            return null;
        } catch (IllegalArgumentException exception) {
            return exception.getMessage();
        }
    }

    public static String normalizeQuery(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > 100
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("搜索内容最多 100 个字符，不能包含控制字符。");
        }
        return normalized;
    }

    private static String normalizeIdentifier(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > 50
                || normalized.chars().anyMatch(Character::isISOControl)) return "";
        return normalized;
    }
}
