package com.example.library.controller;

import com.example.library.loan.LoanListItem;
import com.example.library.loan.LoanQueryService;
import com.example.library.loan.LoanSearchState;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class LoanQueryController {

    private final LoanQueryService queries;
    private final CurrentIdentityService currentIdentity;

    public LoanQueryController(LoanQueryService queries, CurrentIdentityService currentIdentity) {
        this.queries = queries;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/staff/loans")
    public String staffLoans(@RequestParam(required = false) String reader,
                             @RequestParam(required = false) String barcode,
                             @RequestParam(required = false) String state,
                             @RequestParam(required = false) String fromDate,
                             @RequestParam(required = false) String toDate,
                             @RequestParam(defaultValue = "0") String page,
                             Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("reader", valueOrEmpty(reader));
        model.addAttribute("barcode", valueOrEmpty(barcode));
        model.addAttribute("state", normalizedState(state));
        model.addAttribute("fromDate", valueOrEmpty(fromDate));
        model.addAttribute("toDate", valueOrEmpty(toDate));
        model.addAttribute("page", valueOrEmpty(page));
        try {
            int pageIndex = parsePage(page);
            LocalDate start = parseDate(fromDate, "开始日期");
            LocalDate end = parseDate(toDate, "结束日期");
            model.addAttribute("loanPage", queries.searchAll(actor.role(), reader, barcode, state, start, end, pageIndex));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("loanPage", emptyPage());
            model.addAttribute("error", exception.getMessage());
        }
        return "loan/staff-list";
    }

    @GetMapping("/reader/loans")
    public String readerLoans(@RequestParam(required = false) String state,
                              @RequestParam(required = false) String fromDate,
                              @RequestParam(required = false) String toDate,
                              @RequestParam(defaultValue = "0") String page,
                              Model model) {
        Long readerUserId = currentIdentity.requireReaderUserId();
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        model.addAttribute("state", normalizedState(state));
        model.addAttribute("fromDate", valueOrEmpty(fromDate));
        model.addAttribute("toDate", valueOrEmpty(toDate));
        model.addAttribute("page", valueOrEmpty(page));
        try {
            int pageIndex = parsePage(page);
            LocalDate start = parseDate(fromDate, "开始日期");
            LocalDate end = parseDate(toDate, "结束日期");
            // Reader identity comes only from the authenticated session; request parameters cannot choose a profile.
            model.addAttribute("loanPage", queries.searchMine(readerUserId, state, start, end, pageIndex));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("loanPage", emptyPage());
            model.addAttribute("error", exception.getMessage());
        }
        return "loan/reader-list";
    }

    private static int parsePage(String value) {
        try {
            int page = Integer.parseInt(value == null || value.isBlank() ? "0" : value);
            if (page < 0) throw new NumberFormatException();
            return page;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("页码格式无效");
        }
    }

    private static LocalDate parseDate(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(label + "格式无效，请使用年-月-日");
        }
    }

    private static String normalizedState(String state) {
        try {
            return LoanSearchState.parse(state).name();
        } catch (IllegalArgumentException exception) {
            return valueOrEmpty(state);
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Page<LoanListItem> emptyPage() {
        return Page.empty(PageRequest.of(0, LoanQueryService.PAGE_SIZE));
    }
}
