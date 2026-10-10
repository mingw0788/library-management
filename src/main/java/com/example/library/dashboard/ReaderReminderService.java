package com.example.library.dashboard;

import com.example.library.account.*;
import com.example.library.loan.LoanRules;
import com.example.library.reservation.ReservationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ReaderReminderService {
    private static final int LIMIT = 5;
    private final EntityManager entityManager;
    private final ReaderProfileRepository readers;
    private final AppUserRepository users;
    private final LoanRules rules;
    private final Clock clock;
    private final ObjectProvider<ReservationService> reservations;

    public ReaderReminderService(EntityManager entityManager, ReaderProfileRepository readers, AppUserRepository users,
                                  LoanRules rules, Clock circulationClock, ObjectProvider<ReservationService> reservations) {
        this.entityManager = entityManager;
        this.readers = readers;
        this.users = users;
        this.rules = rules;
        this.clock = circulationClock;
        this.reservations = reservations;
    }

    @Transactional(readOnly = true)
    public ReaderReminders forReader(Long authenticatedUserId) {
        AppUser account = authenticatedUserId == null ? null : users.findById(authenticatedUserId).orElse(null);
        if (account == null || account.getRole() != UserRole.READER || account.getStatus() != UserStatus.ACTIVE) {
            throw new AccessDeniedException("需要有效的读者身份");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LocalDate today = now.atZone(rules.policy().zoneId()).toLocalDate();
        String zone = rules.policy().businessTimeZone();
        boolean enabled = reservations.getIfAvailable() != null;
        var profile = readers.findByUserId(authenticatedUserId).orElse(null);
        if (profile == null) return new ReaderReminders(false, today, zone, enabled, 0, 0, 0, List.of(), List.of(), List.of());
        Long readerId = profile.getId();
        var overdue = loanItems(readerId, today, true);
        var dueSoon = loanItems(readerId, today, false);
        long readyCount = 0;
        List<ReadyReminderItem> ready = List.of();
        if (enabled) {
            String from = "from Reservation r join Book b on b.id = r.bookId where r.readerId = :reader "
                    + "and r.status = com.example.library.reservation.ReservationStatus.READY and r.holdExpiresAt > :now";
            readyCount = readyParameters(entityManager.createQuery("select count(r) " + from, Long.class), readerId, now).getSingleResult();
            ready = readyParameters(entityManager.createQuery("select new com.example.library.dashboard.ReadyReminderItem(" +
                            "r.id, r.bookId, b.title, r.holdExpiresAt) " + from + " order by r.holdExpiresAt, r.id", ReadyReminderItem.class), readerId, now)
                    .setMaxResults(LIMIT).getResultList();
        }
        return new ReaderReminders(true, today, zone, enabled, overdue.count, dueSoon.count, readyCount,
                overdue.items, dueSoon.items, ready);
    }

    private LoanGroup loanItems(Long readerId, LocalDate today, boolean overdue) {
        String from = "from Loan l join l.copy c join c.book b where l.reader.id = :reader and l.returnedAt is null "
                + (overdue ? "and l.dueDate < :today" : "and l.dueDate >= :today and l.dueDate <= :until");
        long count = loanParameters(entityManager.createQuery("select count(l) " + from, Long.class), readerId, today, overdue).getSingleResult();
        var items = loanParameters(entityManager.createQuery("select new com.example.library.dashboard.LoanReminderItem(" +
                        "l.id, b.id, b.title, l.dueDate) " + from + " order by l.dueDate, l.id", LoanReminderItem.class), readerId, today, overdue)
                .setMaxResults(LIMIT).getResultList();
        return new LoanGroup(count, items);
    }

    private <T> TypedQuery<T> loanParameters(TypedQuery<T> query, Long reader, LocalDate today, boolean overdue) {
        query.setParameter("reader", reader).setParameter("today", today);
        if (!overdue) query.setParameter("until", today.plusDays(3));
        return query;
    }

    private <T> TypedQuery<T> readyParameters(TypedQuery<T> query, Long reader, Instant now) {
        return query.setParameter("reader", reader).setParameter("now", now);
    }

    private record LoanGroup(long count, List<LoanReminderItem> items) {}
}
