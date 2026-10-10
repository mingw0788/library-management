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

    @Column(name = "copy_id")
    private Long copyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

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
        if (status != ReservationStatus.PENDING && status != ReservationStatus.READY) {
            throw new IllegalArgumentException("该预约已结束，不能重复取消");
        }
        if (at == null) throw new IllegalArgumentException("取消时刻不能为空");
        status = ReservationStatus.CANCELLED;
        cancelledAt = at;
    }

    public void assignCopy(Long copyId, Instant holdExpiresAt) {
        if (status != ReservationStatus.PENDING || copyId == null || copyId < 1 || holdExpiresAt == null
                || holdExpiresAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("预约无法分配该副本");
        }
        this.copyId = copyId;
        this.holdExpiresAt = holdExpiresAt;
        this.status = ReservationStatus.READY;
    }

    public void fulfill(Instant at) {
        if (status != ReservationStatus.READY || copyId == null || holdExpiresAt == null
                || at == null || at.isBefore(createdAt) || !at.isBefore(holdExpiresAt)) {
            throw new IllegalArgumentException("该预约当前不能办理取书");
        }
        status = ReservationStatus.FULFILLED;
        fulfilledAt = at;
    }

    public void expire(Instant at) {
        if (status != ReservationStatus.READY || copyId == null || holdExpiresAt == null
                || at == null || at.isBefore(holdExpiresAt)) {
            throw new IllegalArgumentException("该预约尚未到期，不能结束保留");
        }
        status = ReservationStatus.EXPIRED;
        expiredAt = at;
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        boolean pending = status == ReservationStatus.PENDING;
        boolean ready = status == ReservationStatus.READY;
        boolean fulfilled = status == ReservationStatus.FULFILLED;
        boolean cancelled = status == ReservationStatus.CANCELLED;
        boolean expired = status == ReservationStatus.EXPIRED;
        boolean validAssignment = (pending && copyId == null && holdExpiresAt == null && fulfilledAt == null)
                && expiredAt == null
                || (ready && copyId != null && copyId > 0 && holdExpiresAt != null
                && fulfilledAt == null && expiredAt == null)
                || (fulfilled && copyId != null && copyId > 0 && holdExpiresAt != null
                && fulfilledAt != null && expiredAt == null)
                || (expired && copyId != null && copyId > 0 && holdExpiresAt != null
                && fulfilledAt == null && expiredAt != null)
                || (cancelled && fulfilledAt == null
                && expiredAt == null
                && ((copyId == null && holdExpiresAt == null) || (copyId != null && holdExpiresAt != null)));
        if (readerId == null || readerId < 1 || bookId == null || bookId < 1 || status == null
                || createdAt == null || ((pending || ready || fulfilled || expired) != (cancelledAt == null))
                || (cancelled && cancelledAt == null) || !validAssignment
                || (cancelledAt != null && cancelledAt.isBefore(createdAt))
                || (holdExpiresAt != null && holdExpiresAt.isBefore(createdAt))
                || (fulfilledAt != null && (fulfilledAt.isBefore(createdAt) || !fulfilledAt.isBefore(holdExpiresAt)))
                || (expiredAt != null && (expiredAt.isBefore(createdAt) || expiredAt.isBefore(holdExpiresAt)))
                || version < 0) {
            throw new IllegalStateException("预约记录不符合约束");
        }
    }

    public Long getId() { return id; }
    public Long getReaderId() { return readerId; }
    public Long getBookId() { return bookId; }
    public Long getCopyId() { return copyId; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public Instant getFulfilledAt() { return fulfilledAt; }
    public Instant getExpiredAt() { return expiredAt; }
    public long getVersion() { return version; }
}
