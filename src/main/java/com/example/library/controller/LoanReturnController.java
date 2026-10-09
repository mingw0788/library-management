package com.example.library.controller;

import com.example.library.loan.Loan;
import com.example.library.loan.LoanReturnService;
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
public class LoanReturnController {

    private final LoanReturnService returns;
    private final CurrentIdentityService currentIdentity;

    public LoanReturnController(LoanReturnService returns, CurrentIdentityService currentIdentity) {
        this.returns = returns;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/staff/loans/return")
    public String page(Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
        return "loan/return";
    }

    @PostMapping("/staff/loans/return")
    public String returnLoan(@RequestParam(required = false) Long loanId,
                             @RequestParam(required = false) String barcode,
                             RedirectAttributes redirect) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        boolean hasLoanId = loanId != null;
        boolean hasBarcode = barcode != null && !barcode.trim().isEmpty();
        if (hasLoanId == hasBarcode) {
            FormFeedback.failure(redirect, "LOAN_RETURN", "请只填写一个借阅编号或一个馆藏条码。",
                    FormFeedback.safeValues("loanId", loanId, "barcode", barcode), "loanId", "barcode");
            return "redirect:/staff/loans/return";
        }

        try {
            Loan loan = hasLoanId
                    ? returns.returnByLoanId(actor.userId(), actor.role(), loanId)
                    : returns.returnByBarcode(actor.userId(), actor.role(), barcode);
            redirect.addFlashAttribute("notice", "归还已办理。借阅编号：" + loan.getId()
                    + "；副本已恢复为可借状态。");
        } catch (IllegalArgumentException exception) {
            FormFeedback.failure(redirect, "LOAN_RETURN", exception.getMessage(),
                    FormFeedback.safeValues("loanId", loanId, "barcode", barcode), "loanId", "barcode");
        } catch (DataIntegrityViolationException exception) {
            FormFeedback.failure(redirect, "LOAN_RETURN", "借阅资料已发生变化，请重新核对后重试。",
                    FormFeedback.safeValues("loanId", loanId, "barcode", barcode), "loanId", "barcode");
        } catch (TransientDataAccessException exception) {
            FormFeedback.failure(redirect, "LOAN_RETURN", "该借阅正在被其他请求处理，请刷新状态后重试。",
                    FormFeedback.safeValues("loanId", loanId, "barcode", barcode), "loanId", "barcode");
        }
        return "redirect:/staff/loans/return";
    }
}
