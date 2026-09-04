package com.portfolio.ragchatbot.controller;

import com.portfolio.ragchatbot.repository.QuestionLogRepository;
import com.portfolio.ragchatbot.service.AdminSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Sola lettura per il pannello Analytics: le domande più frequenti. */
@RestController
public class QuestionLogController {

    private final QuestionLogRepository questionLogRepository;
    private final AdminSessionService adminSessionService;

    public QuestionLogController(QuestionLogRepository questionLogRepository, AdminSessionService adminSessionService) {
        this.questionLogRepository = questionLogRepository;
        this.adminSessionService = adminSessionService;
    }

    @GetMapping("/api/admin/questions")
    public ResponseEntity<?> topQuestions(HttpServletRequest httpRequest) {
        boolean isAdmin = adminSessionService.isValidToken(
                HttpRequests.cookie(httpRequest, AdminController.ADMIN_COOKIE_NAME));
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Non autorizzato."));
        }

        List<Item> items = questionLogRepository.topQuestions(15).stream()
                .map(q -> new Item(q.question(), q.count(),
                        q.lastAsked() == null ? null : q.lastAsked().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
                .toList();
        return ResponseEntity.ok(new Response(items));
    }

    public record MessageResponse(String message) {
    }

    public record Item(String question, int count, String lastAsked) {
    }

    public record Response(List<Item> items) {
    }
}
