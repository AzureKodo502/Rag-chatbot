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
        // SameSite=None; Secure: il frontend standalone (index.html aperto come file://
        // o servito da un'altra origine) fa richieste cross-site verso questo backend.
        // Con SameSite=Lax il browser NON rimanda il cookie in quelle richieste, quindi
        // la modalità sviluppatore non verrebbe mai riconosciuta. Secure è accettato
        // anche su http://localhost, e in produzione il backend è comunque su HTTPS.
        String cookieHeader = ADMIN_COOKIE_NAME + "=" + token
                + "; Path=/; Max-Age=43200; HttpOnly; SameSite=None; Secure";
        response.addHeader("Set-Cookie", cookieHeader);
        return ResponseEntity.ok().body("Modalità sviluppatore attiva per 12 ore.");
    }

    public record LoginRequest(String password) {
    }
}
