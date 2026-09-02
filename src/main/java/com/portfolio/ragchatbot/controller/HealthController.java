package com.portfolio.ragchatbot.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint di health pubblico. Due usi:
 * - il frontend lo chiama a fine cold-start per svegliare il DB (Neon free
 *   tier va in sleep dopo 5 min) prima che il visitatore faccia la prima
 *   domanda;
 * - un monitor esterno (o la GitHub Action `keep-alive`) lo pinga ogni ~10
 *   min per tenere svegli backend e DB.
 *
 * La query `count(*)` è banale ma tocca il DB: è proprio quello che serve
 * per riscaldare la connessione e il query planner.
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public Health health() {
        long start = System.currentTimeMillis();
        Integer chunks = jdbcTemplate.queryForObject("SELECT count(*) FROM document_chunks", Integer.class);
        long dbMs = System.currentTimeMillis() - start;
        return new Health("ok", chunks == null ? 0 : chunks, dbMs);
    }

    public record Health(String status, int chunks, long dbLatencyMs) {
    }
}
