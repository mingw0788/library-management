package com.example.library.controller;

import com.example.library.loan.Loan;
import com.example.library.loan.LoanRenewalService;
import com.example.library.security.CurrentIdentityService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LoanRenewalController {

    private final LoanRenewalService renewals;
    private final CurrentIdentityService currentIdentity;

    public LoanRenewalController(LoanRenewalService renewals, CurrentIdentityService currentIdentity) {
        this.renewals = renewals;
        this.currentIdentity = currentIdentity;
    }

    @PostMapping("/reader/loans/{loanId}/renew")
    public String renew(@PathVariable Long loanId, RedirectAttributes redirect) {
        Long readerUserId = currentIdentity.requireReaderUserId();
        try {
            Loan loan = renewals.renewForReader(readerUserId, loanId);
            redirect.addFlashAttribute("notice", "续借成功，新的应还日期为 " + loan.getDueDate() + "。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("error", "借阅资料已发生变化，请刷新页面后重试。");
        } catch (TransientDataAccessException exception) {
            redirect.addFlashAttribute("error", "该借阅正在被其他请求处理，请刷新页面后重试。");
        }
        return "redirect:/reader/loans";
    }
}
