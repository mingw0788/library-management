package com.example.library.catalog;

public record BookCopyInventory(long available, long onLoan, long held, long withdrawn) {
    public BookCopyInventory(long available, long onLoan, long withdrawn) {
        this(available, onLoan, 0, withdrawn);
    }

    public long total() { return available + onLoan + held + withdrawn; }
}
