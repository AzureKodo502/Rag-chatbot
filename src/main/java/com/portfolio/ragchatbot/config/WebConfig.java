package com.portfolio.ragchatbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS centralizzato e configurabile.
 *
 * In produzione il frontend è servito da Spring sulla stessa origine del
 * backend: nessuna richiesta cross-origin, quindi CORS resta DISATTIVO
 * (`app.cors.allowed-origins` vuoto) e il browser non ne ha bisogno.
 *
 * Per lo sviluppo con il frontend servito da un'altra origine (es.
 * `http://127.0.0.1:8000`), impostare `CORS_ALLOWED_ORIGINS` con l'elenco
 * separato da virgola.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] RATE_LIMIT_HEADERS = {
            "X-RateLimit-Remaining-Minute", "X-RateLimit-Remaining-Day",
            "X-RateLimit-Reset-Minute", "X-RateLimit-Reset-Day", "X-Admin-Bypass"
    };

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return; // stessa origine: nessuna regola CORS
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split("\\s*,\\s*"))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type")
                .exposedHeaders(RATE_LIMIT_HEADERS)
                .allowCredentials(true)
                .maxAge(1800);
    }
}
