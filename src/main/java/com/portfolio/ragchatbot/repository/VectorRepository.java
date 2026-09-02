package com.portfolio.ragchatbot.repository;

import com.portfolio.ragchatbot.model.DocumentChunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Repository
public class VectorRepository {

    private final JdbcTemplate jdbcTemplate;

    public VectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(DocumentChunk chunk) {
        String sql = "INSERT INTO document_chunks (source, content, embedding) VALUES (?, ?, CAST(? AS vector))";
        jdbcTemplate.update(sql, chunk.getSource(), chunk.getContent(), toVectorLiteral(chunk.getEmbedding()));
    }

    /**
     * Ritorna i top-k chunk più simili (distanza coseno) alla query.
     * L'operatore "<=>" è fornito da pgvector: 0 = identici, valori più alti = più distanti.
     */
    public List<DocumentChunk> findMostSimilar(float[] queryEmbedding, int topK) {
        String sql = """
            SELECT id, source, content, 1 - (embedding <=> CAST(? AS vector)) AS similarity
            FROM document_chunks
            ORDER BY embedding <=> CAST(? AS vector)
            LIMIT ?
            """;
        String vectorLiteral = toVectorLiteral(queryEmbedding);

        System.out.println("DEBUG: queryEmbedding.length = " + queryEmbedding.length);
        System.out.println("DEBUG: vectorLiteral (primi 120 char) = "
                + vectorLiteral.substring(0, Math.min(120, vectorLiteral.length())));

        Long totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_chunks", Long.class);
        System.out.println("DEBUG: conteggio totale visto da QUESTA connessione = " + totalCount);

        List<DocumentChunk> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(rs.getLong("id"));
            chunk.setSource(rs.getString("source"));
            chunk.setContent(rs.getString("content"));
            chunk.setSimilarity(rs.getDouble("similarity"));
            return chunk;
        }, vectorLiteral, vectorLiteral, topK);

        System.out.println("DEBUG: righe restituite dalla query = " + results.size());
        return results;
    }

    public void deleteAll() {
        jdbcTemplate.update("DELETE FROM document_chunks");
    }

    /** Tutti i chunk, per fonte e ordine di inserimento. Serve alla scheda di
     *  sintesi, che va costruita sull'intera KB e non su un top-k. */
    public List<DocumentChunk> findAll() {
        return jdbcTemplate.query("SELECT id, source, content FROM document_chunks ORDER BY source, id",
                (rs, rowNum) -> {
                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setId(rs.getLong("id"));
                    chunk.setSource(rs.getString("source"));
                    chunk.setContent(rs.getString("content"));
                    return chunk;
                });
    }

    private String toVectorLiteral(float[] embedding) {
        String joined = IntStream.range(0, embedding.length)
                .mapToObj(i -> Float.toString(embedding[i]))
                .collect(Collectors.joining(","));
        return "[" + joined + "]";
    }
}
