package com.example.library.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "book")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String author;

    @Column(length = 200)
    private String publisher;

    @Column(name = "published_date")
    private LocalDate publishedDate;

    @Column(length = 13)
    private String isbn;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Book() {}

    Book(Long categoryId, String title, String author, String publisher, LocalDate publishedDate,
         String isbn, String description) {
        this.categoryId = categoryId;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.publishedDate = publishedDate;
        this.isbn = isbn;
        this.description = description;
        this.status = BookStatus.ACTIVE;
        this.version = 0;
    }

    public void update(Long categoryId, String title, String author, String publisher,
                       LocalDate publishedDate, String isbn, String description) {
        if (status != BookStatus.ACTIVE) throw new IllegalArgumentException("已归档书目不能修改");
        this.categoryId = categoryId;
        this.title = title;
        this.author = author;
        this.publisher = publisher;
        this.publishedDate = publishedDate;
        this.isbn = isbn;
        this.description = description;
    }

    public void archive() {
        if (status == BookStatus.ARCHIVED) throw new IllegalArgumentException("书目已经归档");
        status = BookStatus.ARCHIVED;
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (categoryId == null || title == null || title.isBlank() || !title.equals(title.trim())
                || title.codePointCount(0, title.length()) > 200
                || author == null || author.isBlank() || !author.equals(author.trim())
                || author.codePointCount(0, author.length()) > 200
                || (publisher != null && (publisher.codePointCount(0, publisher.length()) > 200
                || !publisher.equals(publisher.trim())))
                || (isbn != null && (isbn.length() != 10 && isbn.length() != 13))
                || (description != null && description.codePointCount(0, description.length()) > 2000)
                || status == null || version < 0) {
            throw new IllegalStateException("书目资料不符合约束");
        }
    }

    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getPublisher() { return publisher; }
    public LocalDate getPublishedDate() { return publishedDate; }
    public String getIsbn() { return isbn; }
    public String getDescription() { return description; }
    public BookStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
