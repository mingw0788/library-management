package com.example.library.loan;

public record LoanListItem(Long loanId, String readerNo, String readerName, String barcode, String bookTitle,
                           String borrowedAt, String dueDate, String returnedAt,
                           String checkoutOperatorName, String returnOperatorName,
                           boolean overdue, boolean returnedLate, int renewCount, boolean renewalAvailable) {
}
