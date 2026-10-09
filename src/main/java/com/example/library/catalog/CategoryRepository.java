package com.example.library.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);
    List<Category> findAllByOrderByNameAsc();
    List<Category> findByNameContainingOrderByNameAsc(String namePart);
}
