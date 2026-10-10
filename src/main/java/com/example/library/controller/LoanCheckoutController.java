package com.example.library.controller;

import com.example.library.loan.Loan;
import com.example.library.loan.LoanCheckoutService;
import com.example.library.loan.CheckoutSelectionService;
import com.example.library.loan.CheckoutReaderChoice;
import com.example.library.loan.CheckoutCopyChoice;
import com.example.library.loan.LoanRules;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import com.example.library.web.FormFeedback;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LoanCheckoutController {

    private final LoanCheckoutService checkout;
    private final CurrentIdentityService currentIdentity;
    private final CheckoutSelectionService selections;
    private final LoanRules rules;

    public LoanCheckoutController(LoanCheckoutService checkout, CurrentIdentityService currentIdentity,
                                  CheckoutSelectionService selections, LoanRules rules) {
        this.checkout = checkout;
        this.currentIdentity = currentIdentity;
        this.selections = selections;
        this.rules = rules;
    }

    @GetMapping("/staff/loans/checkout")
    public String page(@RequestParam(defaultValue = "") String readerQuery,
                       @RequestParam(defaultValue = "") String bookQuery,
                       @RequestParam(defaultValue = "") String readerNo,
                       @RequestParam(defaultValue = "") String barcode, Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        // Recover the selection after a rejected POST without trusting display values from the client.
        if ("LOAN_CHECKOUT".equals(model.getAttribute("formKey"))
                && model.getAttribute("formValues") instanceof java.util.Map<?, ?> values) {
            readerNo = values.get("readerNo") instanceof String value ? value : "";
            barcode = values.get("barcode") instanceof String value ? value : "";
        }
        CheckoutReaderChoice selectedReader = selections.reader(readerNo);
        CheckoutCopyChoice selectedCopy = selections.copy(barcode);
        model.addAttribute("selectedReader", selectedReader);
        model.addAttribute("selectedCopy", selectedCopy);
        model.addAttribute("selectedReaderNo", selectedReader == null ? "" : selectedReader.readerNo());
        model.addAttribute("selectedBarcode", selectedCopy == null ? "" : selectedCopy.barcode());
        model.addAttribute("dueDate", rules.dueDateNow());
        model.addAttribute("loanPeriodDays", rules.policy().loanPeriodDays());
        model.addAttribute("maximumActiveLoans", rules.policy().maximumActiveLoans());
        if ((!readerNo.isBlank() && selectedReader == null) || (!barcode.isBlank() && selectedCopy == null)) {
            model.addAttribute("selectionError", "所选读者未启用或副本已不可借，请重新搜索选择。");
        }
        if (selectedReader != null && selectedCopy != null) {
            model.addAttribute("checkoutRestriction", selections.restriction(selectedReader, selectedCopy));
        }
        try {
            readerQuery = CheckoutSelectionService.normalizeQuery(readerQuery);
            bookQuery = CheckoutSelectionService.normalizeQuery(bookQuery);
            model.addAttribute("readerChoices", selections.readers(readerQuery));
            model.addAttribute("copyChoices", selections.copies(bookQuery));
            model.addAttribute("readerQuery", readerQuery);
            model.addAttribute("bookQuery", bookQuery);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("selectionError", exception.getMessage());
            model.addAttribute("readerChoices", java.util.List.of());
            model.addAttribute("copyChoices", java.util.List.of());
            model.addAttribute("readerQuery", "");
            model.addAttribute("bookQuery", "");
        }
        return "loan/checkout";
    }

    @PostMapping("/staff/loans/checkout")
    public String checkout(@RequestParam(required = false) String readerNo,
                           @RequestParam(required = false) String barcode,
                           RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        try {
            Loan loan = checkout.checkout(actor.userId(), actor.role(), readerNo, barcode);
            redirect.addFlashAttribute("notice", "借出已办理。借阅编号：" + loan.getId()
                    + "；应还日期：" + loan.getDueDate() + "。请将应还日期告知读者。");
        } catch (IllegalArgumentException exception) {
            FormFeedback.failure(redirect, "LOAN_CHECKOUT", exception.getMessage(),
                    FormFeedback.safeValues("readerNo", readerNo, "barcode", barcode), "readerNo", "barcode");
        } catch (DataIntegrityViolationException exception) {
            FormFeedback.failure(redirect, "LOAN_CHECKOUT",
                    "借阅数据已发生变化，请重新核对读者和馆藏状态后重试。",
                    FormFeedback.safeValues("readerNo", readerNo, "barcode", barcode), "readerNo", "barcode");
        } catch (TransientDataAccessException exception) {
            FormFeedback.failure(redirect, "LOAN_CHECKOUT",
                    "借阅正在被其他请求处理，请刷新馆藏状态后重试。",
                    FormFeedback.safeValues("readerNo", readerNo, "barcode", barcode), "readerNo", "barcode");
        }
        return "redirect:/staff/loans/checkout";
    }
}
