package com.portfolio.ragchatbot.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Helper condivisi tra i controller: lettura/scrittura cookie e IP del client. */
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

    /**
     * Scrive un cookie HttpOnly per l'app.
     *
     * SameSite=Lax: frontend e backend sono sulla stessa origine (Spring serve
     * anche l'HTML), quindi non servono cookie cross-site e Lax dà anche una
     * protezione CSRF di base. Secure: la prod è su HTTPS ed è accettato anche
     * su http://localhost.
     */
    static void setAppCookie(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        response.addHeader("Set-Cookie", name + "=" + value
                + "; Path=/; Max-Age=" + maxAgeSeconds + "; HttpOnly; SameSite=Lax; Secure");
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
