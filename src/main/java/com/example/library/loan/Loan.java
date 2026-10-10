package com.example.library.loan;

import com.example.library.account.AppUser;
import com.example.library.account.ReaderProfile;
import com.example.library.catalog.BookCopy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reader_id", nullable = false, updatable = false)
    private ReaderProfile reader;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "copy_id", nullable = false, updatable = false)
    private BookCopy copy;

    @Column(name = "borrowed_at", nullable = false, updatable = false)
    private Instant borrowedAt;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checkout_operator_id", nullable = false, updatable = false)
    private AppUser checkoutOperator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_operator_id")
    private AppUser returnOperator;

    @Column(name = "active_copy_id", insertable = false, updatable = false)
    private Long activeCopyId;

    @Column(name = "renew_count", nullable = false)
    private int renewCount;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Loan() {}

    Loan(ReaderProfile reader, BookCopy copy, Instant borrowedAt, LocalDate dueDate, AppUser checkoutOperator) {
        this.reader = reader;
        this.copy = copy;
        this.borrowedAt = borrowedAt;
        this.dueDate = dueDate;
        this.checkoutOperator = checkoutOperator;
        this.renewCount = 0;
        this.version = 0;
    }

    public static Loan fromReservationPickup(ReaderProfile reader, BookCopy copy, Instant borrowedAt,
                                             LocalDate dueDate, AppUser checkoutOperator) {
        return new Loan(reader, copy, borrowedAt, dueDate, checkoutOperator);
    }

    public void returnTo(Instant returnedAt, AppUser returnOperator) {
        if (this.returnedAt != null || returnedAt == null || returnOperator == null) {
            throw new IllegalArgumentException("该借阅已归还或归还资料不完整");
        }
        if (returnedAt.isBefore(borrowedAt)) {
            throw new IllegalArgumentException("归还时刻不能早于借出时刻");
        }
        this.returnedAt = returnedAt;
        this.returnOperator = returnOperator;
    }

    public void renew(int extensionDays) {
        if (returnedAt != null) throw new IllegalArgumentException("已归还的借阅不能续借");
        if (renewCount >= 1) throw new IllegalArgumentException("每条借阅记录最多续借一次");
        if (extensionDays < 1) throw new IllegalArgumentException("续借天数必须大于 0");
        this.dueDate = dueDate.plusDays(extensionDays);
        this.renewCount = Math.addExact(renewCount, 1);
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (reader == null || copy == null || borrowedAt == null || dueDate == null || checkoutOperator == null
                || (returnedAt == null) != (returnOperator == null)
                || (returnedAt != null && returnedAt.isBefore(borrowedAt))
                || renewCount < 0 || renewCount > 1 || version < 0) {
            throw new IllegalStateException("借阅记录不符合约束");
        }
    }

    public Long getId() { return id; }
    public ReaderProfile getReader() { return reader; }
    public Long getReaderId() { return reader.getId(); }
    public BookCopy getCopy() { return copy; }
    public Long getCopyId() { return copy.getId(); }
    public Instant getBorrowedAt() { return borrowedAt; }
    public LocalDate getDueDate() { return dueDate; }
    public Instant getReturnedAt() { return returnedAt; }
    public AppUser getCheckoutOperator() { return checkoutOperator; }
    public AppUser getReturnOperator() { return returnOperator; }
    public Long getActiveCopyId() { return activeCopyId; }
    public int getRenewCount() { return renewCount; }
    public long getVersion() { return version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
