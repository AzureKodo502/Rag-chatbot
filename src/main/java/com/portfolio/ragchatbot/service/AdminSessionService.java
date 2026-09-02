package com.portfolio.ragchatbot.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Gestisce la "modalità sviluppatore": una password (mai nel codice, solo in
 * una variabile d'ambiente) che se corretta emette un token temporaneo,
 * salvato lato server, che esenta le richieste dal rate limiting.
 */
@Service
public class AdminSessionService {

    @Value("${admin.bypass-password:}")
    private String configuredPassword;

    private final Cache<String, Boolean> validTokens = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(12))
            .maximumSize(100)
            .build();

    /** @return un nuovo token se la password è corretta, altrimenti null. */
    public String login(String password) {
        if (configuredPassword == null || configuredPassword.isBlank()) {
            return null; // modalità sviluppatore disattivata se non è configurata nessuna password
        }
        if (!configuredPassword.equals(password)) {
            return null;
        }
        String token = UUID.randomUUID().toString();
        validTokens.put(token, Boolean.TRUE);
        return token;
    }

    public boolean isValidToken(String token) {
        return token != null && validTokens.getIfPresent(token) != null;
    }
}