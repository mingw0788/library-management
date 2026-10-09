package com.example.library.catalog;

public record BookCopyInventory(long available, long onLoan, long withdrawn) {
    public long total() { return available + onLoan + withdrawn; }
}
