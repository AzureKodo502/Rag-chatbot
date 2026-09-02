package com.portfolio.ragchatbot.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class FeedbackRepository {

    private final JdbcTemplate jdbcTemplate;

    public FeedbackRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(int rating, String comment, String lang, Integer interactions) {
        String sql = "INSERT INTO feedback (rating, comment, lang, interactions) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, rating, comment, lang, interactions);
    }

    /** Voti più recenti prima. Cap difensivo: la dashboard non deve mai scaricare tutto. */
    public List<FeedbackRow> findRecent(int limit) {
        String sql = """
                SELECT id, rating, comment, lang, interactions, created_at
                FROM feedback
                ORDER BY created_at DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new FeedbackRow(
                rs.getLong("id"),
                rs.getInt("rating"),
                rs.getString("comment"),
                rs.getString("lang"),
                (Integer) rs.getObject("interactions"),
                rs.getTimestamp("created_at").toLocalDateTime()
        ), limit);
    }

    public Summary summary() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feedback", Long.class);
        if (count == null || count == 0) {
            return new Summary(0, 0.0, Map.of(1, 0L, 2, 0L, 3, 0L, 4, 0L, 5, 0L));
        }
        Double average = jdbcTemplate.queryForObject("SELECT AVG(rating) FROM feedback", Double.class);

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            distribution.put(star, 0L);
        }
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT rating, COUNT(*) AS n FROM feedback GROUP BY rating")) {
            distribution.put(((Number) row.get("rating")).intValue(), ((Number) row.get("n")).longValue());
        }

        double rounded = average == null ? 0.0 : Math.round(average * 100.0) / 100.0;
        return new Summary(count, rounded, distribution);
    }

    public record FeedbackRow(long id, int rating, String comment, String lang,
                              Integer interactions, LocalDateTime createdAt) {
    }

    public record Summary(long count, double average, Map<Integer, Long> distribution) {
    }
}
