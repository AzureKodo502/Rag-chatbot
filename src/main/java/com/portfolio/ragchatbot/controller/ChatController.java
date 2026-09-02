package com.portfolio.ragchatbot.controller;

import com.portfolio.ragchatbot.service.AdminSessionService;
import com.portfolio.ragchatbot.service.ChatAnswer;
import com.portfolio.ragchatbot.service.ChatService;
import com.portfolio.ragchatbot.service.PromptGuardService;
import com.portfolio.ragchatbot.service.RateLimitService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(originPatterns = "*", allowCredentials = "true",
        exposedHeaders = {"X-RateLimit-Remaining-Minute", "X-RateLimit-Remaining-Day",
                "X-RateLimit-Reset-Minute", "X-RateLimit-Reset-Day", "X-Admin-Bypass"})
public class ChatController {

    private static final String SESSION_COOKIE_NAME = "rag_session_id";

    private final ChatService chatService;
    private final RateLimitService rateLimitService;
    private final PromptGuardService promptGuardService;
    private final AdminSessionService adminSessionService;

    @Value("${rag.input.max-length}")
    private int maxInputLength;

    public ChatController(ChatService chatService, RateLimitService rateLimitService,
                          PromptGuardService promptGuardService, AdminSessionService adminSessionService) {
        this.chatService = chatService;
        this.rateLimitService = rateLimitService;
        this.promptGuardService = promptGuardService;
        this.adminSessionService = adminSessionService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String clientIp = resolveClientIp(httpRequest);
        String sessionId = resolveOrCreateSessionId(httpRequest, httpResponse);
        boolean isAdmin = adminSessionService.isValidToken(readCookie(httpRequest, AdminController.ADMIN_COOKIE_NAME));

        if (isAdmin) {
            return withAdminHeaders(ResponseEntity.ok()).body("{}");
        }

        RateLimitService.RateLimitStatus rateLimit = rateLimitService.peekStatus(clientIp, sessionId);
        return withRateLimitHeaders(ResponseEntity.ok(), rateLimit).body("{}");
    }

    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatRequest request, HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        String clientIp = resolveClientIp(httpRequest);
        String sessionId = resolveOrCreateSessionId(httpRequest, httpResponse);
        boolean isAdmin = adminSessionService.isValidToken(readCookie(httpRequest, AdminController.ADMIN_COOKIE_NAME));

        if (!isAdmin) {
            RateLimitService.RateLimitStatus rateLimit = rateLimitService.checkAndRecord(clientIp, sessionId);
            if (!rateLimit.allowed()) {
                return withRateLimitHeaders(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS), rateLimit)
                        .body(new ErrorResponse(rateLimit.errorMessage()));
            }
        }

        String message = request.message() == null ? "" : request.message().trim();
        if (message.isEmpty()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Il messaggio non può essere vuoto."));
        }
        if (message.length() > maxInputLength) {
            return ResponseEntity.badRequest().body(new ErrorResponse(
                    "Il messaggio è troppo lungo (massimo " + maxInputLength + " caratteri)."));
        }

        ResponseEntity.BodyBuilder responseBuilder = isAdmin
                ? withAdminHeaders(ResponseEntity.ok())
                : withRateLimitHeaders(ResponseEntity.ok(), rateLimitService.peekStatus(clientIp, sessionId));

        if (promptGuardService.isSuspicious(message)) {
            ChatAnswer refusal = new ChatAnswer(PromptGuardService.REFUSAL_MESSAGE, List.of(), null);
            return responseBuilder.body(refusal);
        }

        ChatAnswer result = chatService.answer(message);
        return responseBuilder.body(result);
    }

    private ResponseEntity.BodyBuilder withRateLimitHeaders(ResponseEntity.BodyBuilder builder,
                                                            RateLimitService.RateLimitStatus status) {
        builder.header("X-RateLimit-Remaining-Minute", String.valueOf(status.remainingMinute()))
                .header("X-RateLimit-Remaining-Day", String.valueOf(status.remainingDay()));
        if (status.minuteResetEpochMs() != null) {
            builder.header("X-RateLimit-Reset-Minute", String.valueOf(status.minuteResetEpochMs()));
        }
        if (status.dayResetEpochMs() != null) {
            builder.header("X-RateLimit-Reset-Day", String.valueOf(status.dayResetEpochMs()));
        }
        return builder;
    }

    private ResponseEntity.BodyBuilder withAdminHeaders(ResponseEntity.BodyBuilder builder) {
        return builder.header("X-Admin-Bypass", "true");
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveOrCreateSessionId(HttpServletRequest request, HttpServletResponse response) {
        String existing = readCookie(request, SESSION_COOKIE_NAME);
        if (existing != null) {
            return existing;
        }
        String newSessionId = UUID.randomUUID().toString();
        // SameSite=None; Secure: vedi nota in AdminController. Il frontend è cross-site
        // rispetto al backend, quindi con SameSite=Lax questo cookie non tornerebbe mai
        // indietro e il layer di rate limiting per sessione sarebbe di fatto disattivato
        // (resterebbe solo quello per IP).
        String cookieHeader = SESSION_COOKIE_NAME + "=" + newSessionId
                + "; Path=/; Max-Age=2592000; HttpOnly; SameSite=None; Secure";
        response.addHeader("Set-Cookie", cookieHeader);
        return newSessionId;
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

    public record ChatRequest(String message) {
    }

    public record ErrorResponse(String error) {
    }
}