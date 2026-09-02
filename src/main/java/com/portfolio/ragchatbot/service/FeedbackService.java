package com.portfolio.ragchatbot.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.portfolio.ragchatbot.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Raccoglie il feedback a stelle della card in-app.
 *
 * La logica di QUANDO mostrare la card (dopo 3 domande, cooldown di 48h su
 * "Non ora" / X, "Non chiedermelo più") vive tutta nel frontend via
 * localStorage: il backend qui si limita ad accettare e salvare, con una
 * piccola difesa anti-spam per IP (non persiste l'IP, lo usa solo come
 * chiave in memoria e a scadenza).
 */
@Service
public class FeedbackService {

    static final int MAX_COMMENT_LENGTH = 1000;
    // La card compare una volta sola per browser (localStorage), quindi un
    // utente normale invia una volta. Questo cap serve solo a fermare lo
    // spam scriptato, ma va tenuto abbastanza alto da non penalizzare più
    // persone dietro lo stesso IP (NAT aziendale, wifi condiviso).
    private static final int MAX_PER_IP_PER_DAY = 20;

    private final FeedbackRepository feedbackRepository;

    private final Cache<String, AtomicInteger> perIpDaily = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofDays(1))
            .maximumSize(50_000)
            .build();

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public sealed interface Result {
        record Saved() implements Result {
        }

        record Rejected(String message) implements Result {
        }

        record TooMany() implements Result {
        }
    }

    public Result submit(Integer rating, String rawComment, String rawLang, Integer rawInteractions, String ipKey) {
        if (rating == null || rating < 1 || rating > 5) {
            return new Result.Rejected("Seleziona una valutazione da 1 a 5 stelle.");
        }

        String comment = rawComment == null ? null : rawComment.trim();
        if (comment != null && comment.isEmpty()) {
            comment = null;
        }
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            return new Result.Rejected("Il commento è troppo lungo (massimo " + MAX_COMMENT_LENGTH + " caratteri).");
        }

        String lang = "en".equalsIgnoreCase(rawLang) ? "en"
                : "it".equalsIgnoreCase(rawLang) ? "it"
                : null;

        Integer interactions = rawInteractions == null ? null : Math.max(0, Math.min(rawInteractions, 9999));

        AtomicInteger todayCount = perIpDaily.get(ipKey, k -> new AtomicInteger(0));
        if (todayCount.incrementAndGet() > MAX_PER_IP_PER_DAY) {
            return new Result.TooMany();
        }

        feedbackRepository.save(rating, comment, lang, interactions);
        return new Result.Saved();
    }

    public Dashboard dashboard() {
        FeedbackRepository.Summary summary = feedbackRepository.summary();
        List<FeedbackRepository.FeedbackRow> items = feedbackRepository.findRecent(500);
        return new Dashboard(summary, items);
    }

    public record Dashboard(FeedbackRepository.Summary summary, List<FeedbackRepository.FeedbackRow> items) {
    }
}
