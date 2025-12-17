package com.thynkah.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AskController {

    @GetMapping("/askui")
    public String askUi() {
        return "ask"; // templates/ask.html
    }
}
