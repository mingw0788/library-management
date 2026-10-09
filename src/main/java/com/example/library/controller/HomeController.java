package com.example.library.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Principal principal, Model model) {
        model.addAttribute("authenticated", principal != null);
        if (principal != null) {
            model.addAttribute("loginName", principal.getName());
        }
        return "index";
    }
}
