package com.portfolio.ragchatbot.controller;

import com.portfolio.ragchatbot.repository.FeedbackRepository;
import com.portfolio.ragchatbot.service.AdminSessionService;
import com.portfolio.ragchatbot.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final AdminSessionService adminSessionService;

    public FeedbackController(FeedbackService feedbackService, AdminSessionService adminSessionService) {
        this.feedbackService = feedbackService;
        this.adminSessionService = adminSessionService;
    }

    @PostMapping("/api/feedback")
    public ResponseEntity<?> submit(@RequestBody FeedbackRequest request, HttpServletRequest httpRequest) {
        FeedbackService.Result result = feedbackService.submit(
                request.rating(), request.comment(), request.lang(), request.interactions(),
                HttpRequests.clientIp(httpRequest));

        if (result instanceof FeedbackService.Result.Saved) {
            return ResponseEntity.status(HttpStatus.CREATED).body(new MessageResponse("ok"));
        }
        if (result instanceof FeedbackService.Result.TooMany) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new MessageResponse("Hai già inviato feedback di recente. Grazie!"));
        }
        String message = ((FeedbackService.Result.Rejected) result).message();
        return ResponseEntity.badRequest().body(new MessageResponse(message));
    }

    @GetMapping("/api/admin/feedback")
    public ResponseEntity<?> dashboard(HttpServletRequest httpRequest) {
        boolean isAdmin = adminSessionService.isValidToken(
                HttpRequests.cookie(httpRequest, AdminController.ADMIN_COOKIE_NAME));
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse("Non autorizzato."));
        }

        FeedbackService.Dashboard data = feedbackService.dashboard();
        List<FeedbackItem> items = data.items().stream()
                .map(FeedbackController::toItem)
                .toList();
        return ResponseEntity.ok(new DashboardResponse(
                new SummaryResponse(data.summary().count(), data.summary().average(), data.summary().distribution()),
                items));
    }

    private static FeedbackItem toItem(FeedbackRepository.FeedbackRow row) {
        String createdAt = row.createdAt() == null ? null
                : row.createdAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return new FeedbackItem(row.id(), row.rating(), row.comment(), row.lang(), row.interactions(), createdAt);
    }

    public record FeedbackRequest(Integer rating, String comment, String lang, Integer interactions) {
    }

    public record MessageResponse(String message) {
    }

    public record DashboardResponse(SummaryResponse summary, List<FeedbackItem> items) {
    }

    public record SummaryResponse(long count, double average, Map<Integer, Long> distribution) {
    }

    public record FeedbackItem(long id, int rating, String comment, String lang,
                               Integer interactions, String createdAt) {
    }
}
