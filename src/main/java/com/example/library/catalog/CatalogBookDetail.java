package com.example.library.catalog;

import java.util.List;

public record CatalogBookDetail(Book book, Category category, List<CatalogCopyView> copies, long availableCount) {}
