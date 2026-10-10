package com.example.library.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "book_copy")
public class BookCopy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, length = 50)
    private String barcode;

    @Column(name = "shelf_location", length = 100)
    private String shelfLocation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookCopyStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected BookCopy() {}

    BookCopy(Book book, String barcode, String shelfLocation) {
        this.book = book;
        this.barcode = barcode;
        this.shelfLocation = shelfLocation;
        this.status = BookCopyStatus.AVAILABLE;
    }

    public void withdraw() {
        if (status != BookCopyStatus.AVAILABLE) {
            throw new IllegalArgumentException("只有可借副本可以注销");
        }
        status = BookCopyStatus.WITHDRAWN;
    }

    public void checkout() {
        if (status != BookCopyStatus.AVAILABLE) {
            throw new IllegalArgumentException("该副本当前不可借");
        }
        status = BookCopyStatus.ON_LOAN;
    }

    public void holdForReservation() {
        if (status != BookCopyStatus.AVAILABLE) {
            throw new IllegalArgumentException("只有可借副本可以分配给预约");
        }
        status = BookCopyStatus.HELD;
    }

    public void releaseReservationHold() {
        if (status != BookCopyStatus.HELD) {
            throw new IllegalArgumentException("该副本当前没有预约保留");
        }
        status = BookCopyStatus.AVAILABLE;
    }

    public void checkoutFromReservationHold() {
        if (status != BookCopyStatus.HELD) {
            throw new IllegalArgumentException("该副本当前没有预约保留");
        }
        status = BookCopyStatus.ON_LOAN;
    }

    public void returnFromLoan() {
        if (status != BookCopyStatus.ON_LOAN) {
            throw new IllegalArgumentException("该副本当前没有借出，不能办理归还");
        }
        status = BookCopyStatus.AVAILABLE;
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (book == null || barcode == null || barcode.isBlank() || !barcode.equals(barcode.trim())
                || barcode.codePointCount(0, barcode.length()) > 50
                || (shelfLocation != null && (!shelfLocation.equals(shelfLocation.trim())
                || shelfLocation.codePointCount(0, shelfLocation.length()) > 100))
                || status == null || version < 0) {
            throw new IllegalStateException("副本资料不符合约束");
        }
    }

    public Long getId() { return id; }
    public Book getBook() { return book; }
    public Long getBookId() { return book.getId(); }
    public String getBarcode() { return barcode; }
    public String getShelfLocation() { return shelfLocation; }
    public BookCopyStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
