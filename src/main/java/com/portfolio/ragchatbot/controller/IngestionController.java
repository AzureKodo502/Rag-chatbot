package com.portfolio.ragchatbot.controller;

import com.portfolio.ragchatbot.service.IngestionService;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint di comodo per popolare la knowledge base in locale.
 * IMPORTANTE: prima del deploy pubblico, proteggi questo controller
 * (es. con una chiave admin) o rimuovilo del tutto e fai l'ingestion
 * solo in locale/CI.
 */
@RestController
@RequestMapping("/api/admin/ingest")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    public String ingest(@RequestBody IngestRequest request) {
        int chunks = ingestionService.ingest(request.source(), request.text());
        return "Salvati " + chunks + " chunk per la sorgente '" + request.source() + "'";
    }

    @DeleteMapping
    public String reset() {
        ingestionService.resetKnowledgeBase();
        return "Knowledge base svuotata";
    }

    public record IngestRequest(String source, String text) {
    }
}
