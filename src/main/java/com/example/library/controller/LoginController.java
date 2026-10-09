package com.example.library.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(@RequestParam(name = "error", required = false) String error,
                        @RequestParam(name = "logout", required = false) String logout,
                        @RequestParam(name = "changed", required = false) String changed,
                        @RequestParam(name = "expired", required = false) String expired,
                        Model model) {
        if (error != null) {
            model.addAttribute("errorMessage", "用户名或密码错误，或账号不可用。");
        }
        if (logout != null) {
            model.addAttribute("noticeMessage", "你已安全退出。");
        }
        if (changed != null) {
            model.addAttribute("noticeMessage", "密码已修改，请使用新密码重新登录。");
        }
        if (expired != null) {
            model.addAttribute("noticeMessage", "登录状态已变化，请重新登录。");
        }
        return "login";
    }
}
