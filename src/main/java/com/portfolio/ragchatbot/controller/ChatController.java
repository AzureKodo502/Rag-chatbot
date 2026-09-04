package com.portfolio.ragchatbot.controller;

import com.portfolio.ragchatbot.repository.QuestionLogRepository;
import com.portfolio.ragchatbot.service.AdminSessionService;
import com.portfolio.ragchatbot.service.ChatAnswer;
import com.portfolio.ragchatbot.service.ChatService;
import com.portfolio.ragchatbot.service.PromptGuardService;
import com.portfolio.ragchatbot.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final String SESSION_COOKIE_NAME = "rag_session_id";

    private final ChatService chatService;
    private final RateLimitService rateLimitService;
    private final PromptGuardService promptGuardService;
    private final AdminSessionService adminSessionService;
    private final QuestionLogRepository questionLogRepository;

    @Value("${rag.input.max-length}")
    private int maxInputLength;

    public ChatController(ChatService chatService, RateLimitService rateLimitService,
                          PromptGuardService promptGuardService, AdminSessionService adminSessionService,
                          QuestionLogRepository questionLogRepository) {
        this.chatService = chatService;
        this.rateLimitService = rateLimitService;
        this.promptGuardService = promptGuardService;
        this.adminSessionService = adminSessionService;
        this.questionLogRepository = questionLogRepository;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String clientIp = HttpRequests.clientIp(httpRequest);
        String sessionId = resolveOrCreateSessionId(httpRequest, httpResponse);
        boolean isAdmin = adminSessionService.isValidToken(HttpRequests.cookie(httpRequest, AdminController.ADMIN_COOKIE_NAME));

        if (isAdmin) {
            return withAdminHeaders(ResponseEntity.ok()).body("{}");
        }

        RateLimitService.RateLimitStatus rateLimit = rateLimitService.peekStatus(clientIp, sessionId);
        return withRateLimitHeaders(ResponseEntity.ok(), rateLimit).body("{}");
    }

    @PostMapping
    public ResponseEntity<?> chat(@RequestBody ChatRequest request, HttpServletRequest httpRequest,
                                  HttpServletResponse httpResponse) {
        String clientIp = HttpRequests.clientIp(httpRequest);
        String sessionId = resolveOrCreateSessionId(httpRequest, httpResponse);
        boolean isAdmin = adminSessionService.isValidToken(HttpRequests.cookie(httpRequest, AdminController.ADMIN_COOKIE_NAME));

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

        String lang = "en".equalsIgnoreCase(request.lang()) ? "en"
                : "it".equalsIgnoreCase(request.lang()) ? "it"
                : null;
        // Il log è solo per l'Analytics: se fallisce (es. migrazione non ancora
        // applicata, DB momentaneamente giù) non deve mai far fallire la risposta.
        try {
            questionLogRepository.log(message, lang);
        } catch (Exception e) {
            log.warn("Log della domanda fallito (non bloccante): {}", e.getMessage());
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

    private String resolveOrCreateSessionId(HttpServletRequest request, HttpServletResponse response) {
        String existing = HttpRequests.cookie(request, SESSION_COOKIE_NAME);
        if (existing != null) {
            return existing;
        }
        String newSessionId = UUID.randomUUID().toString();
        HttpRequests.setAppCookie(response, SESSION_COOKIE_NAME, newSessionId, 30 * 24 * 60 * 60); // 30 giorni
        return newSessionId;
    }

    public record ChatRequest(String message, String lang) {
    }

    public record ErrorResponse(String error) {
    }
}