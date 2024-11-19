package com.example.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;

@Controller()
public class HomeController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @GetMapping("/")
    public String root(HttpSession session, Model model) {
        Integer count = (Integer) session.getAttribute("count");
        count = (count == null) ? 1 : count + 1;
        session.setAttribute("count", count);

        // 톰캣 인스턴스 식별 정보 추가
        String instance = System.getProperty("jvmRoute", "unknown-instance");
        model.addAttribute("sessionCount", count);
        model.addAttribute("instance", instance);
        return "home";
    }

    @GetMapping("/home")
    public String home(HttpSession session, Model model) {
        Integer count = (Integer) session.getAttribute("count");
        count = (count == null) ? 1 : count + 1;
        session.setAttribute("count", count);

        model.addAttribute("sessionCount", count);
        return "home";
    }

    @GetMapping("/static")
    public String staticPage(HttpSession session, Model model) {
        return "static";
    }

    @RequestMapping("/db")
    public String getDataFromDB(@RequestParam String name, Model model) {
        jdbcTemplate.update("INSERT INTO users (name) VALUES (?)", name);
        String userName = jdbcTemplate.queryForObject("SELECT name FROM users WHERE name = ?", new Object[]{name}, String.class);
        model.addAttribute("userName", userName);
        return "db";
    }
}
