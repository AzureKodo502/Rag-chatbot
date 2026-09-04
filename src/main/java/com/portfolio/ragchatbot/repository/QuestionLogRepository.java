package com.portfolio.ragchatbot.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class QuestionLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public QuestionLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void log(String question, String lang) {
        jdbcTemplate.update("INSERT INTO question_log (question, lang) VALUES (?, ?)", question, lang);
    }

    /** Le domande più ricorrenti per testo esatto: intercetta anche i click
     *  sulle chip preimpostate, che arrivano sempre con lo stesso testo. */
    public List<TopQuestion> topQuestions(int limit) {
        String sql = """
                SELECT question, COUNT(*) AS n, MAX(created_at) AS last_asked
                FROM question_log
                GROUP BY question
                ORDER BY n DESC, last_asked DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new TopQuestion(
                rs.getString("question"),
                rs.getInt("n"),
                rs.getTimestamp("last_asked").toLocalDateTime()
        ), limit);
    }

    public record TopQuestion(String question, int count, LocalDateTime lastAsked) {
    }
}
