package com.payment.access.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 控制台页面入口。
 */
@Controller
public class ConsolePageController {

    @GetMapping({"/console", "/console/"})
    public String consoleHome() {
        return "redirect:/console/index.html";
    }
}
