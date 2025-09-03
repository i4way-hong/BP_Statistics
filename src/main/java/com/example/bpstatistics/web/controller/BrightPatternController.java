package com.example.bpstatistics.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import com.example.bpstatistics.service.BrightPatternAuthService;

@Controller
@RequestMapping("/brightpattern")
public class BrightPatternController {

    private final BrightPatternAuthService authService;

    public BrightPatternController(BrightPatternAuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("title", "BrightPattern Demo");
        model.addAttribute("tokenInfo", authService.getTokenInfo());
        return "index"; // templates/index.html
    }
}
