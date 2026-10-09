package com.example.library.account;

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
@Table(name = "reader_profile", uniqueConstraints = {
        @UniqueConstraint(name = "uk_reader_profile_user_id", columnNames = "user_id"),
        @UniqueConstraint(name = "uk_reader_profile_reader_no", columnNames = "reader_no")
})
public class ReaderProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "reader_no", nullable = false, length = 50, updatable = false)
    private String readerNo;

    @Column(length = 100)
    private String contact;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected ReaderProfile() {}

    ReaderProfile(Long userId, String readerNo, String contact) {
        this.userId = userId;
        this.readerNo = readerNo;
        this.contact = contact;
    }

    public void changeContact(String contact) { this.contact = contact; }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (userId == null || readerNo == null || readerNo.isBlank()
                || readerNo.codePointCount(0, readerNo.length()) > 50
                || (contact != null && contact.codePointCount(0, contact.length()) > 100)) {
            throw new IllegalStateException("读者档案数据不符合约束");
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getReaderNo() { return readerNo; }
    public String getContact() { return contact; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
