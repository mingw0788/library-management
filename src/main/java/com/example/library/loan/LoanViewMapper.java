package com.example.library.loan;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Component
public class LoanViewMapper {

    private static final DateTimeFormatter BUSINESS_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LoanRules rules;
    private final Clock clock;

    public LoanViewMapper(LoanRules rules, Clock circulationClock) {
        this.rules = rules;
        this.clock = circulationClock;
    }

    public LoanListItem toListItem(LoanViewRow row) {
        Instant returnedAt = row.returnedAt();
        boolean overdue = returnedAt == null && rules.isOverdue(row.dueDate(), clock.instant());
        boolean returnedLate = returnedAt != null && rules.isReturnedLate(row.dueDate(), returnedAt);
        return new LoanListItem(row.loanId(), row.readerNo(), row.readerName(), row.barcode(), row.bookTitle(),
                BUSINESS_TIME.format(row.borrowedAt().atZone(rules.policy().zoneId())), row.dueDate().toString(),
                returnedAt == null ? null : BUSINESS_TIME.format(returnedAt.atZone(rules.policy().zoneId())),
                row.checkoutOperatorName(), row.returnOperatorName(), overdue, returnedLate);
    }
}
