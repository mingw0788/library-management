package com.example.library.loan;

import com.example.library.account.ReaderProfile;
import com.example.library.account.ReaderProfileRepository;
import com.example.library.account.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class LoanQueryService {

    public static final int PAGE_SIZE = 10;
    private static final int MAX_PAGE_INDEX = 100_000;

    private final LoanRepository loans;
    private final ReaderProfileRepository readers;
    private final LoanViewMapper viewMapper;
    private final CirculationPolicy policy;
    private final LoanRules rules;

    public LoanQueryService(LoanRepository loans, ReaderProfileRepository readers,
                            LoanViewMapper viewMapper, CirculationPolicy policy, LoanRules rules) {
        this.loans = loans;
        this.readers = readers;
        this.viewMapper = viewMapper;
        this.policy = policy;
        this.rules = rules;
    }

    @Transactional(readOnly = true)
    public Page<LoanListItem> searchAll(UserRole actorRole, String readerQuery, String barcodeQuery,
                                        String state, LocalDate fromDate, LocalDate toDate, int page) {
        requireStaff(actorRole);
        return search(null, readerQuery, barcodeQuery, state, fromDate, toDate, page);
    }

    @Transactional(readOnly = true)
    public Page<LoanListItem> searchMine(Long authenticatedReaderUserId, String state,
                                         LocalDate fromDate, LocalDate toDate, int page) {
        if (authenticatedReaderUserId == null) throw new AccessDeniedException("需要有效的读者身份");
        ReaderProfile profile = readers.findByUserId(authenticatedReaderUserId)
                .orElseThrow(() -> new AccessDeniedException("当前读者档案不存在"));
        boolean readerHasOverdueLoan = loans.existsByReader_IdAndReturnedAtIsNullAndDueDateBefore(
                profile.getId(), rules.today());
        return search(profile.getId(), null, null, state, fromDate, toDate, page, true, readerHasOverdueLoan);
    }

    private Page<LoanListItem> search(Long readerProfileId, String readerQuery, String barcodeQuery,
                                      String state, LocalDate fromDate, LocalDate toDate, int page) {
        return search(readerProfileId, readerQuery, barcodeQuery, state, fromDate, toDate, page, false, false);
    }

    private Page<LoanListItem> search(Long readerProfileId, String readerQuery, String barcodeQuery,
                                      String state, LocalDate fromDate, LocalDate toDate, int page,
                                      boolean readerRenewalEnabled, boolean readerHasOverdueLoan) {
        if (page < 0 || page > MAX_PAGE_INDEX) throw new IllegalArgumentException("页码超出允许范围");
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("借出日期起始值不能晚于结束值");
        }
        String normalizedReader = optionalQuery(readerQuery, "读者查询", 100);
        String normalizedBarcode = optionalQuery(barcodeQuery, "馆藏条码", 50);
        LoanSearchState selectedState = LoanSearchState.parse(state);
        Instant fromInclusive;
        Instant untilExclusive;
        try {
            fromInclusive = fromDate == null ? null : fromDate.atStartOfDay(policy.zoneId()).toInstant();
            untilExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay(policy.zoneId()).toInstant();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("借出日期范围无效");
        }
        Pageable pageable = PageRequest.of(page, PAGE_SIZE,
                Sort.by(Sort.Order.desc("borrowedAt"), Sort.Order.desc("id")));
        Page<LoanViewRow> result = loans.search(readerProfileId, normalizedReader, normalizedBarcode,
                selectedState.name(), fromInclusive, untilExclusive, pageable);
        if (result.getTotalPages() > 0 && page >= result.getTotalPages()) {
            PageRequest lastPage = PageRequest.of(result.getTotalPages() - 1, PAGE_SIZE,
                    Sort.by(Sort.Order.desc("borrowedAt"), Sort.Order.desc("id")));
            result = loans.search(readerProfileId, normalizedReader, normalizedBarcode,
                    selectedState.name(), fromInclusive, untilExclusive, lastPage);
        }
        boolean canOfferRenewal = readerRenewalEnabled && !readerHasOverdueLoan;
        return result.map(row -> viewMapper.toListItem(row, canOfferRenewal));
    }

    private static String optionalQuery(String value, String label, int maximumLength) {
        if (value == null || value.trim().isEmpty()) return null;
        String normalized = value.trim();
        if (normalized.codePointCount(0, normalized.length()) > maximumLength
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(label + "长度不能超过 " + maximumLength + " 个字符");
        }
        return normalized;
    }

    private static void requireStaff(UserRole role) {
        if (role != UserRole.ADMIN && role != UserRole.LIBRARIAN) {
            throw new AccessDeniedException("仅管理员或馆员可以查询全馆借阅");
        }
    }
}
