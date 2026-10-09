package com.example.library.catalog;

public record BookCopySummary(Long bookId, BookCopyStatus status, long quantity) {}
