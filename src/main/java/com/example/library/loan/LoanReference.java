package com.example.library.loan;

public record LoanReference(Long loanId, Long readerId, Long readerUserId, Long copyId, Long bookId) {
}
