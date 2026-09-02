package com.portfolio.ragchatbot.controller;

import com.portfolio.ragchatbot.service.AdminSessionService;
import com.portfolio.ragchatbot.service.SummaryService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Scheda "Genera Riassunto".
 *
 * `POST /api/summary` è pubblico e NON passa dal rate limiter: la parte
 * generata dall'LLM è in cache per lingua (vedi {@link SummaryService}),
 * quindi un abuser ottiene al massimo due chiamate API (it + en) per
 * riavvio del backend, poi solo cache.
 */
@RestController
@CrossOrigin(originPatterns = "*", allowCredentials = "true")
public class SummaryController {

    private final SummaryService summaryService;
    private final AdminSessionService adminSessionService;

    public SummaryController(SummaryService summaryService, AdminSessionService adminSessionService) {
        this.summaryService = summaryService;
        this.adminSessionService = adminSessionService;
    }

    @PostMapping("/api/summary")
    public ResponseEntity<?> generate(@RequestBody(required = false) SummaryRequest request) {
        String lang = (request == null || request.lang() == null) ? "it" : request.lang();
        try {
            return ResponseEntity.ok(summaryService.summary(lang));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new MessageResponse("Non è stato possibile generare il riassunto. Riprova tra poco."));
        }
    }

    @PostMapping("/api/admin/summary/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest httpRequest) {
        boolean isAdmin = adminSessionService.isValidToken(
                readCookie(httpRequest, AdminController.ADMIN_COOKIE_NAME));
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Non autorizzato."));
        }
        summaryService.refresh();
        return ResponseEntity.ok(new MessageResponse("Cache del riassunto svuotata; verrà rigenerato alla prossima richiesta."));
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public record SummaryRequest(String lang) {
    }

    public record MessageResponse(String message) {
    }
}
