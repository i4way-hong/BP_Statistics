package com.example.bpstatistics.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.example.bpstatistics.service.BrightPatternAuthService;

@Controller
public class HomeController {

    private final BrightPatternAuthService authService;

    public HomeController(BrightPatternAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("message", "BP Sample14 Home");
        model.addAttribute("tokenInfo", authService.getTokenInfo());
        return "index"; // templates/index.html 기대
    }
}
