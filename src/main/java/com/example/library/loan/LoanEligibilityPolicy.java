package com.example.library.loan;

import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.ReaderProfile;
import com.example.library.account.ReaderProfileRepository;
import com.example.library.account.UserRole;
import com.example.library.account.UserStatus;
import com.example.library.catalog.BookCopy;
import com.example.library.catalog.BookCopyRepository;
import com.example.library.catalog.BookCopyStatus;
import com.example.library.catalog.BookStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoanEligibilityPolicy {

    private final ReaderProfileRepository readers;
    private final AppUserRepository users;
    private final BookCopyRepository copies;
    private final LoanRepository loans;
    private final LoanRules rules;

    public LoanEligibilityPolicy(ReaderProfileRepository readers, AppUserRepository users,
                                 BookCopyRepository copies, LoanRepository loans, LoanRules rules) {
        this.readers = readers;
        this.users = users;
        this.copies = copies;
        this.loans = loans;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public void assertCanBorrow(Long readerProfileId, Long copyId) {
        ReaderProfile reader = readers.findById(readerProfileId)
                .orElseThrow(() -> new IllegalArgumentException("读者档案不存在"));
        AppUser account = users.findById(reader.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("读者账号不存在"));
        if (account.getRole() != UserRole.READER || account.getStatus() != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("读者账号未启用，不能办理借阅");
        }
        BookCopy copy = copies.findWithBookById(copyId)
                .orElseThrow(() -> new IllegalArgumentException("馆藏副本不存在"));
        if (copy.getBook().getStatus() != BookStatus.ACTIVE) {
            throw new IllegalArgumentException("书目已归档，不能办理借阅");
        }
        if (copy.getStatus() != BookCopyStatus.AVAILABLE) {
            throw new IllegalArgumentException("该副本当前不可借");
        }
        if (loans.existsByReader_IdAndReturnedAtIsNullAndDueDateBefore(reader.getId(), rules.today())) {
            throw new IllegalArgumentException("读者有逾期未还图书，不能继续借阅");
        }
        if (loans.countByReader_IdAndReturnedAtIsNull(reader.getId()) >= rules.policy().maximumActiveLoans()) {
            throw new IllegalArgumentException("读者同时借阅数量已达到上限");
        }
        if (loans.existsByReader_IdAndReturnedAtIsNullAndCopy_Book_Id(reader.getId(), copy.getBook().getId())) {
            throw new IllegalArgumentException("读者已有同一书目的未还副本，不能重复借阅");
        }
    }
}
