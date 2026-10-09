package com.example.library.loan;

import com.example.library.account.AccountService;
import com.example.library.account.AppUser;
import com.example.library.account.AppUserRepository;
import com.example.library.account.ReaderProfile;
import com.example.library.account.ReaderProfileRepository;
import com.example.library.account.UserRole;
import com.example.library.catalog.Book;
import com.example.library.catalog.BookCopy;
import com.example.library.catalog.BookCopyRepository;
import com.example.library.catalog.BookCopyService;
import com.example.library.catalog.BookRepository;
import com.example.library.catalog.BookService;
import com.example.library.catalog.Category;
import com.example.library.catalog.CategoryRepository;
import com.example.library.catalog.CategoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class LoanModelTest {

    private static final String PASSWORD = "loan-test-pass-123";

    @Autowired private LoanRepository loans;
    @Autowired private LoanEligibilityPolicy eligibility;
    @Autowired private LoanRules rules;
    @Autowired private CirculationPolicy policy;
    @Autowired private AccountService accounts;
    @Autowired private AppUserRepository users;
    @Autowired private ReaderProfileRepository readers;
    @Autowired private BookService bookService;
    @Autowired private BookRepository books;
    @Autowired private BookCopyService copyService;
    @Autowired private BookCopyRepository copies;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categories;
    @Autowired private JdbcTemplate jdbc;

    private final List<Long> profileIds = new ArrayList<>();
    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> copyIds = new ArrayList<>();
    private final List<Long> bookIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();
    private Category category;
    private ReaderProfile reader;
    private AppUser operator;

    @BeforeEach
    void prepare() {
        category = categoryService.create(UserRole.ADMIN, "T12 分类-" + token());
        categoryIds.add(category.getId());
        reader = newReader();
        operator = newUser(UserRole.LIBRARIAN);
    }

    @AfterEach
    void clean() {
        profileIds.forEach(id -> jdbc.update("DELETE FROM loan WHERE reader_id = ?", id));
        copyIds.forEach(id -> copies.findById(id).ifPresent(copies::delete));
        copies.flush();
        bookIds.forEach(id -> books.findById(id).ifPresent(books::delete));
        books.flush();
        categoryIds.forEach(id -> categories.findById(id).ifPresent(categories::delete));
        categories.flush();
        profileIds.forEach(id -> readers.findById(id).ifPresent(readers::delete));
        readers.flush();
        userIds.forEach(id -> users.findById(id).ifPresent(users::delete));
        profileIds.clear();
        userIds.clear();
        copyIds.clear();
        bookIds.clear();
        categoryIds.clear();
    }

    @Test
    void mysqlGeneratedUniqueKeyAllowsOnlyOneOpenLoanPerCopyAndAllowsReborrowAfterReturn() {
        BookCopy copy = bookCopy("唯一有效借阅 " + token());
        Instant borrowed = Instant.parse("2026-06-01T01:00:00Z");
        Loan first = loans.saveAndFlush(new Loan(reader, copy, borrowed, rules.dueDateFor(borrowed), operator));
        assertEquals(copy.getId(), jdbc.queryForObject(
                "SELECT active_copy_id FROM loan WHERE id = ?", Long.class, first.getId()));
        assertThrows(DataAccessException.class, () -> insertLoan(reader.getId(), copy.getId(), borrowed,
                rules.dueDateFor(borrowed), operator.getId()));

        Instant returnedAt = borrowed.plusSeconds(120);
        jdbc.update("UPDATE loan SET returned_at = ?, return_operator_id = ? WHERE id = ?",
                Timestamp.from(returnedAt), operator.getId(), first.getId());
        assertNull(jdbc.queryForObject("SELECT active_copy_id FROM loan WHERE id = ?", Long.class, first.getId()));

        Loan second = loans.saveAndFlush(new Loan(reader, copy, returnedAt.plusSeconds(1),
                rules.dueDateFor(returnedAt.plusSeconds(1)), operator));
        assertEquals(copy.getId(), jdbc.queryForObject(
                "SELECT active_copy_id FROM loan WHERE id = ?", Long.class, second.getId()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM loan WHERE copy_id = ?", Integer.class, copy.getId()));

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO loan (reader_id, copy_id, borrowed_at, due_date, returned_at, checkout_operator_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?)", reader.getId(), copy.getId(), Timestamp.from(borrowed),
                Date.valueOf(rules.dueDateFor(borrowed)), Timestamp.from(returnedAt), operator.getId()));
    }

    @Test
    void mysqlConstraintsProtectReferencesAndReturnOperatorPair() {
        BookCopy copy = bookCopy("借阅外键 " + token());
        Instant borrowed = Instant.now();
        assertThrows(DataAccessException.class, () -> insertLoan(-1L, copy.getId(), borrowed,
                rules.dueDateFor(borrowed), operator.getId()));
        assertThrows(DataAccessException.class, () -> insertLoan(reader.getId(), copy.getId(), borrowed,
                rules.dueDateFor(borrowed), -1L));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO loan (reader_id, copy_id, borrowed_at, due_date, returned_at, " +
                        "checkout_operator_id, return_operator_id) VALUES (?, ?, ?, ?, ?, ?, NULL)",
                reader.getId(), copy.getId(), Timestamp.from(borrowed), Date.valueOf(rules.dueDateFor(borrowed)),
                Timestamp.from(borrowed.plusSeconds(60)), operator.getId()));
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO loan (reader_id, copy_id, borrowed_at, due_date, checkout_operator_id, renew_count) " +
                        "VALUES (?, ?, ?, ?, ?, 1)", reader.getId(), copy.getId(), Timestamp.from(borrowed),
                Date.valueOf(rules.dueDateFor(borrowed)), operator.getId()));
    }

    @Test
    void borrowingEligibilityChecksAccountCopyBookOverdueQuotaAndSameBook() {
        assertEquals(5, policy.maximumActiveLoans());
        assertEquals(14, policy.loanPeriodDays());
        assertEquals("Asia/Shanghai", policy.businessTimeZone());

        Book firstBook = book("首册资格 " + token());
        BookCopy firstCopy = copyService.register(UserRole.ADMIN, firstBook.getId(), "ELIG-A-" + token(), null);
        copyIds.add(firstCopy.getId());
        BookCopy secondCopy = copyService.register(UserRole.ADMIN, firstBook.getId(), "ELIG-B-" + token(), null);
        copyIds.add(secondCopy.getId());
        assertDoesNotThrow(() -> eligibility.assertCanBorrow(reader.getId(), firstCopy.getId()));
        addLoan(reader, firstCopy, rules.today().plusDays(14));
        assertThrows(IllegalArgumentException.class,
                () -> eligibility.assertCanBorrow(reader.getId(), secondCopy.getId()));

        ReaderProfile overdueReader = newReader();
        Book overdueBook = book("逾期资格 " + token());
        BookCopy overdueCopy = bookCopy(overdueBook);
        addLoan(overdueReader, overdueCopy, rules.today().minusDays(1));
        Book otherBook = book("逾期后续资格 " + token());
        BookCopy otherCopy = bookCopy(otherBook);
        assertThrows(IllegalArgumentException.class,
                () -> eligibility.assertCanBorrow(overdueReader.getId(), otherCopy.getId()));

        ReaderProfile fullReader = newReader();
        for (int index = 0; index < policy.maximumActiveLoans(); index++) {
            Book existingBook = book("额度中已有 " + token());
            addLoan(fullReader, bookCopy(existingBook), rules.today().plusDays(14));
        }
        Book sixthBook = book("额度第六册 " + token());
        BookCopy sixthCopy = bookCopy(sixthBook);
        assertThrows(IllegalArgumentException.class,
                () -> eligibility.assertCanBorrow(fullReader.getId(), sixthCopy.getId()));

        ReaderProfile disabledReader = newReader();
        accounts.disableReader(UserRole.ADMIN, disabledReader.getUserId());
        assertThrows(IllegalArgumentException.class,
                () -> eligibility.assertCanBorrow(disabledReader.getId(), sixthCopy.getId()));

        Book withdrawnBook = book("注销副本资格 " + token());
        BookCopy withdrawnCopy = bookCopy(withdrawnBook);
        copyService.withdraw(UserRole.ADMIN, withdrawnCopy.getId());
        assertThrows(IllegalArgumentException.class,
                () -> eligibility.assertCanBorrow(reader.getId(), withdrawnCopy.getId()));

        Book archivedBook = book("归档书目资格 " + token());
        BookCopy archivedCopy = bookCopy(archivedBook);
        jdbc.update("UPDATE book SET status = 'ARCHIVED' WHERE id = ?", archivedBook.getId());
        assertThrows(IllegalArgumentException.class,
                () -> eligibility.assertCanBorrow(reader.getId(), archivedCopy.getId()));
    }

    private Loan addLoan(ReaderProfile loanReader, BookCopy copy, LocalDate dueDate) {
        Instant borrowed = rules.today().minusDays(1).atStartOfDay(policy.zoneId()).toInstant();
        Loan loan = loans.saveAndFlush(new Loan(loanReader, copy, borrowed, dueDate, operator));
        jdbc.update("UPDATE book_copy SET status = 'ON_LOAN' WHERE id = ?", copy.getId());
        return loan;
    }

    private void insertLoan(Long readerId, Long copyId, Instant borrowed, LocalDate dueDate, Long operatorId) {
        jdbc.update("INSERT INTO loan (reader_id, copy_id, borrowed_at, due_date, checkout_operator_id) " +
                        "VALUES (?, ?, ?, ?, ?)", readerId, copyId, Timestamp.from(borrowed),
                Date.valueOf(dueDate), operatorId);
    }

    private ReaderProfile newReader() {
        String suffix = token();
        AppUser user = accounts.createReader(UserRole.ADMIN, "loan_reader_" + suffix,
                PASSWORD, "借阅测试读者", "LOAN-" + suffix, null);
        userIds.add(user.getId());
        ReaderProfile profile = readers.findByUserId(user.getId()).orElseThrow();
        profileIds.add(profile.getId());
        return profile;
    }

    private AppUser newUser(UserRole role) {
        AppUser user = accounts.createUser("loan_operator_" + token(), PASSWORD, "借阅办理员", role);
        user.markPasswordChanged();
        users.saveAndFlush(user);
        userIds.add(user.getId());
        return user;
    }

    private Book book(String title) {
        Book book = bookService.create(UserRole.ADMIN, title, "测试作者", null, null, null, category.getId(), null);
        bookIds.add(book.getId());
        return book;
    }

    private BookCopy bookCopy(String title) { return bookCopy(book(title)); }

    private BookCopy bookCopy(Book book) {
        BookCopy copy = copyService.register(UserRole.ADMIN, book.getId(), "LOAN-COPY-" + token(), null);
        copyIds.add(copy.getId());
        return copy;
    }

    private static String token() { return UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
}
