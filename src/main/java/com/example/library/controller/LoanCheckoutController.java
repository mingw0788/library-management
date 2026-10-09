package com.example.library.controller;

import com.example.library.loan.Loan;
import com.example.library.loan.LoanCheckoutService;
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

    public LoanCheckoutController(LoanCheckoutService checkout, CurrentIdentityService currentIdentity) {
        this.checkout = checkout;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping("/staff/loans/checkout")
    public String page(Model model) {
        CurrentIdentity actor = currentIdentity.requireCurrentIdentity();
        model.addAttribute("loginName", actor.loginName());
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
