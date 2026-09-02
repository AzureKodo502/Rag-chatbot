package com.portfolio.ragchatbot.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {

    private static final Duration MINUTE_WINDOW = Duration.ofMinutes(1);
    private static final Duration DAY_WINDOW = Duration.ofDays(1);

    @Value("${rag.rate-limit.per-minute}")
    private int perMinuteLimit;

    @Value("${rag.rate-limit.per-day}")
    private int perDayLimit;

    private final Cache<String, AtomicInteger> perMinuteBucket = Caffeine.newBuilder()
            .expireAfterWrite(MINUTE_WINDOW)
            .maximumSize(50_000)
            .build();

    private final Cache<String, AtomicInteger> perDayBucket = Caffeine.newBuilder()
            .expireAfterWrite(DAY_WINDOW)
            .maximumSize(50_000)
            .build();

    public record RateLimitStatus(boolean allowed, int remainingMinute, int remainingDay, String errorMessage,
                                  Long minuteResetEpochMs, Long dayResetEpochMs) {
    }

    public RateLimitStatus checkAndRecord(String ipKey, String sessionKey) {
        KeyState ipState = recordSingleKey(ipKey);
        KeyState sessionState = recordSingleKey(sessionKey);
        return combine(ipState, sessionState);
    }

    public RateLimitStatus peekStatus(String ipKey, String sessionKey) {
        KeyState ipState = peekSingleKey(ipKey);
        KeyState sessionState = peekSingleKey(sessionKey);
        return combine(ipState, sessionState);
    }

    private record KeyState(int minuteUsed, int dayUsed, Long minuteResetEpochMs, Long dayResetEpochMs) {
    }

    private KeyState recordSingleKey(String key) {
        AtomicInteger minuteCount = perMinuteBucket.get(key, k -> new AtomicInteger(0));
        int minuteUsed = minuteCount.incrementAndGet();

        AtomicInteger dayCount = perDayBucket.get(key, k -> new AtomicInteger(0));
        int dayUsed = dayCount.incrementAndGet();

        return new KeyState(minuteUsed, dayUsed,
                resetEpochMs(perMinuteBucket, key, MINUTE_WINDOW),
                resetEpochMs(perDayBucket, key, DAY_WINDOW));
    }

    private KeyState peekSingleKey(String key) {
        AtomicInteger minuteCount = perMinuteBucket.getIfPresent(key);
        AtomicInteger dayCount = perDayBucket.getIfPresent(key);
        int minuteUsed = minuteCount == null ? 0 : minuteCount.get();
        int dayUsed = dayCount == null ? 0 : dayCount.get();

        return new KeyState(minuteUsed, dayUsed,
                minuteCount == null ? null : resetEpochMs(perMinuteBucket, key, MINUTE_WINDOW),
                dayCount == null ? null : resetEpochMs(perDayBucket, key, DAY_WINDOW));
    }

    private RateLimitStatus combine(KeyState ip, KeyState session) {
        // Prendo il conteggio più alto tra i due layer (IP e cookie di sessione):
        // basta che UNO dei due sfori per bloccare.
        int minuteUsed = Math.max(ip.minuteUsed(), session.minuteUsed());
        int dayUsed = Math.max(ip.dayUsed(), session.dayUsed());

        int remainingMinute = Math.max(0, perMinuteLimit - minuteUsed);
        int remainingDay = Math.max(0, perDayLimit - dayUsed);

        Long minuteResetEpochMs = (ip.minuteUsed() >= session.minuteUsed()) ? ip.minuteResetEpochMs() : session.minuteResetEpochMs();
        Long dayResetEpochMs = (ip.dayUsed() >= session.dayUsed()) ? ip.dayResetEpochMs() : session.dayResetEpochMs();

        // checkAndRecord() incrementa PRIMA di chiamare combine(): quindi la N-esima
        // richiesta consentita arriva qui con used == limit. Va accettata (used <= limit),
        // non rifiutata come faceva la vecchia condizione remaining > 0.
        boolean allowed = minuteUsed <= perMinuteLimit && dayUsed <= perDayLimit;
        String errorMessage = null;
        if (!allowed) {
            errorMessage = dayUsed > perDayLimit
                    ? "Hai raggiunto il numero massimo di domande per oggi."
                    : "Troppe richieste in poco tempo. Aspetta un minuto e riprova.";
        }

        return new RateLimitStatus(allowed, remainingMinute, remainingDay, errorMessage,
                minuteResetEpochMs, dayResetEpochMs);
    }

    private Long resetEpochMs(Cache<String, AtomicInteger> cache, String key, Duration windowLength) {
        Optional<Duration> age = cache.policy().expireAfterWrite()
                .flatMap(policy -> policy.ageOf(key));
        return age.map(a -> Instant.now().plus(windowLength.minus(a)).toEpochMilli())
                .orElse(null);
    }
}