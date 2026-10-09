package com.example.library.catalog;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Map;

public record CatalogSearchResult(Page<Book> books, List<Category> categories,
                                  Map<Long, BookCopyInventory> inventory,
                                  Map<Long, String> categoryNames,
                                  String keyword, Long categoryId, String sort) {}
