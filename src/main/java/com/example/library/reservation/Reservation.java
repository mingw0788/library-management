package com.example.library.reservation;

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

import java.time.Instant;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reader_id", nullable = false)
    private Long readerId;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Reservation() {}

    public Reservation(Long readerId, Long bookId, Instant createdAt) {
        this.readerId = readerId;
        this.bookId = bookId;
        this.status = ReservationStatus.PENDING;
        this.createdAt = createdAt;
        this.version = 0;
    }

    public void cancel(Instant at) {
        if (status != ReservationStatus.PENDING) {
            throw new IllegalArgumentException("该预约已结束，不能重复取消");
        }
        if (at == null) throw new IllegalArgumentException("取消时刻不能为空");
        status = ReservationStatus.CANCELLED;
        cancelledAt = at;
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (readerId == null || readerId < 1 || bookId == null || bookId < 1 || status == null
                || createdAt == null || (status == ReservationStatus.PENDING) != (cancelledAt == null)
                || (cancelledAt != null && cancelledAt.isBefore(createdAt)) || version < 0) {
            throw new IllegalStateException("预约记录不符合约束");
        }
    }

    public Long getId() { return id; }
    public Long getReaderId() { return readerId; }
    public Long getBookId() { return bookId; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public long getVersion() { return version; }
}
