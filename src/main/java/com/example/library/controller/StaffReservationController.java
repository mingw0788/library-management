package com.example.library.controller;

import com.example.library.account.UserRole;
import com.example.library.reservation.ReservationService;
import com.example.library.security.CurrentIdentity;
import com.example.library.security.CurrentIdentityService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@Profile({"dev", "test", "prod"})
@ConditionalOnProperty(prefix = "library.reservations", name = "enabled", havingValue = "true")
@RequestMapping("/staff/reservations")
public class StaffReservationController {

    private final ReservationService reservations;
    private final CurrentIdentityService currentIdentity;

    public StaffReservationController(ReservationService reservations, CurrentIdentityService currentIdentity) {
        this.reservations = reservations;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping
    public String readyQueue(Model model) {
        CurrentIdentity identity = currentIdentity.requireCurrentIdentity();
        model.addAttribute("reservations", reservations.listReadyForStaff(identity.role()));
        return "reservation/staff-list";
    }

    @PostMapping("/{reservationId}/fulfill")
    public String fulfill(@PathVariable Long reservationId, @RequestParam String readerNo,
                          RedirectAttributes redirect) {
        CurrentIdentity identity = currentIdentity.requireCurrentIdentity();
        try {
            var result = reservations.fulfillForReader(identity.userId(), identity.role(), reservationId, readerNo);
            if (result.expired()) {
                redirect.addFlashAttribute("error", "该预约已到期，保留副本已释放并按队列重新分配。");
            } else {
                redirect.addFlashAttribute("notice", "已为读者办理取书，借阅记录和预约状态已一起更新。");
            }
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("error", "预约或副本状态刚刚发生变化，请刷新页面后重试。");
        } catch (TransientDataAccessException exception) {
            redirect.addFlashAttribute("error", "预约正在处理中，请刷新页面后重试。");
        }
        return "redirect:/staff/reservations";
    }
}
