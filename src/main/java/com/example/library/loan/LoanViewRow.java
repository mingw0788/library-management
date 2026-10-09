package com.example.library.loan;

import java.time.Instant;
import java.time.LocalDate;

public record LoanViewRow(Long loanId, String readerNo, String readerName, String barcode, String bookTitle,
                          Instant borrowedAt, LocalDate dueDate, Instant returnedAt,
                          String checkoutOperatorName, String returnOperatorName) {
}
