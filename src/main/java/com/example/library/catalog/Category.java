package com.example.library.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "category", uniqueConstraints = @UniqueConstraint(name = "uk_category_name", columnNames = "name"))
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Category() {}

    Category(String name) { this.name = name; }

    public void rename(String name) { this.name = name; }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (name == null || name.isBlank() || !name.equals(name.trim())
                || name.codePointCount(0, name.length()) > 100) {
            throw new IllegalStateException("分类名称须为 1—100 个字符且不能包含首尾空白");
        }
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
