package com.portfolio.ragchatbot.controller;

import com.portfolio.ragchatbot.service.AdminSessionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/login")
public class AdminController {

    public static final String ADMIN_COOKIE_NAME = "rag_admin_token";

    private final AdminSessionService adminSessionService;

    public AdminController(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
    }

    @PostMapping
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        String token = adminSessionService.login(request.password());
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Password non valida.");
        }
        // 12 ore, come la scadenza del token lato server (AdminSessionService)
        HttpRequests.setAppCookie(response, ADMIN_COOKIE_NAME, token, 12 * 60 * 60);
        return ResponseEntity.ok().body("Modalità sviluppatore attiva per 12 ore.");
    }

    public record LoginRequest(String password) {
    }
}
