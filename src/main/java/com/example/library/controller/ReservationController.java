package com.example.library.controller;

import com.example.library.reservation.Reservation;
import com.example.library.reservation.ReservationService;
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
@RequestMapping("/reader/reservations")
public class ReservationController {

    private final ReservationService reservations;
    private final CurrentIdentityService currentIdentity;

    public ReservationController(ReservationService reservations, CurrentIdentityService currentIdentity) {
        this.reservations = reservations;
        this.currentIdentity = currentIdentity;
    }

    @GetMapping
    public String listMine(Model model) {
        model.addAttribute("reservations", reservations.listMine(currentIdentity.requireReaderUserId()));
        return "reservation/reader-list";
    }

    @PostMapping
    public String create(@RequestParam Long bookId, RedirectAttributes redirect) {
        Long readerUserId = currentIdentity.requireReaderUserId();
        try {
            Reservation created = reservations.createForReader(readerUserId, bookId);
            redirect.addFlashAttribute("notice", "已加入预约队列，预约编号为 " + created.getId() + "。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("error", "该书目的预约状态刚刚发生变化，请刷新页面后重试。");
        } catch (TransientDataAccessException exception) {
            redirect.addFlashAttribute("error", "预约请求正在处理中，请刷新页面后重试。");
        }
        return "redirect:/reader/reservations";
    }

    @PostMapping("/{reservationId}/cancel")
    public String cancel(@PathVariable Long reservationId, RedirectAttributes redirect) {
        Long readerUserId = currentIdentity.requireReaderUserId();
        try {
            reservations.cancelForReader(readerUserId, reservationId);
            redirect.addFlashAttribute("notice", "预约已取消。");
        } catch (IllegalArgumentException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        } catch (DataIntegrityViolationException exception) {
            redirect.addFlashAttribute("error", "预约状态刚刚发生变化，请刷新页面后重试。");
        } catch (TransientDataAccessException exception) {
            redirect.addFlashAttribute("error", "预约正在处理中，请刷新页面后重试。");
        }
        return "redirect:/reader/reservations";
    }
}
