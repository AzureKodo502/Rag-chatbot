package com.portfolio.ragchatbot.controller;

import com.portfolio.ragchatbot.service.AdminSessionService;
import com.portfolio.ragchatbot.service.IngestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint per popolare la knowledge base.
 *
 * Protetto dal token "modalità sviluppatore" (cookie {@code rag_admin_token}):
 * l'ingestion e il wipe non devono essere pubblici una volta online. In
 * locale si attiva la dev mode dal frontend, poi si chiamano questi endpoint
 * con lo stesso cookie.
 */
@RestController
@RequestMapping("/api/admin/ingest")
public class IngestionController {

    private final IngestionService ingestionService;
    private final AdminSessionService adminSessionService;

    public IngestionController(IngestionService ingestionService, AdminSessionService adminSessionService) {
        this.ingestionService = ingestionService;
        this.adminSessionService = adminSessionService;
    }

    @PostMapping
    public ResponseEntity<?> ingest(@RequestBody IngestRequest request, HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Non autorizzato.");
        }
        int chunks = ingestionService.ingest(request.source(), request.text());
        return ResponseEntity.ok("Salvati " + chunks + " chunk per la sorgente '" + request.source() + "'");
    }

    @DeleteMapping
    public ResponseEntity<?> reset(HttpServletRequest httpRequest) {
        if (!isAdmin(httpRequest)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Non autorizzato.");
        }
        ingestionService.resetKnowledgeBase();
        return ResponseEntity.ok("Knowledge base svuotata");
    }

    private boolean isAdmin(HttpServletRequest request) {
        return adminSessionService.isValidToken(
                HttpRequests.cookie(request, AdminController.ADMIN_COOKIE_NAME));
    }

    public record IngestRequest(String source, String text) {
    }
}
