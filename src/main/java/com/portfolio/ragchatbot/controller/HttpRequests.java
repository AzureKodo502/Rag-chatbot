package com.portfolio.ragchatbot.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/** Helper condivisi tra i controller: lettura cookie e IP del client. */
final class HttpRequests {

    private HttpRequests() {
    }

    static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    /** Dietro un proxy (es. Render) l'IP reale è il primo valore di X-Forwarded-For. */
    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
