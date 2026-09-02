package com.portfolio.ragchatbot.service;

import com.portfolio.ragchatbot.model.DocumentChunk;
import com.portfolio.ragchatbot.repository.VectorRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class IngestionService {

    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;

    @Value("${rag.chunk.size-words}")
    private int chunkSizeWords;

    @Value("${rag.chunk.overlap-words}")
    private int overlapWords;

    @Value("${rag.ingestion.delay-between-chunks-ms:0}")
    private long delayBetweenChunksMs;

    public IngestionService(EmbeddingService embeddingService, VectorRepository vectorRepository) {
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
    }

    /**
     * Spezza il testo in chunk, genera l'embedding per ciascuno e li salva
     * nel vector store. Tra un chunk e l'altro attende un tempo fisso
     * (rag.ingestion.delay-between-chunks-ms) per non saturare il rate
     * limit del provider di embeddings mandando richieste a raffica —
     * cosa che il retry-on-429 da solo non evita, perché scatta solo DOPO
     * essere stati bloccati.
     */
    public int ingest(String source, String rawText) {
        List<String> chunks = splitIntoChunks(rawText);

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            float[] embedding = embeddingService.embedDocument(chunkText);
            vectorRepository.save(new DocumentChunk(source, chunkText, embedding));

            boolean isLastChunk = (i == chunks.size() - 1);
            if (!isLastChunk && delayBetweenChunksMs > 0) {
                System.out.println("Chunk " + (i + 1) + "/" + chunks.size() + " salvato, attendo "
                        + (delayBetweenChunksMs / 1000) + "s prima del prossimo...");
                sleep(delayBetweenChunksMs);
            }
        }

        return chunks.size();
    }

    public void resetKnowledgeBase() {
        vectorRepository.deleteAll();
    }

    private List<String> splitIntoChunks(String text) {
        String[] rawParagraphs = text.trim().split("\\n\\s*\\n");
        List<String> chunks = new ArrayList<>();

        for (String rawParagraph : rawParagraphs) {
            String paragraph = rawParagraph.trim().replaceAll("\\s+", " ");
            if (paragraph.isEmpty()) continue;

            int wordCount = paragraph.split("\\s+").length;
            if (wordCount <= chunkSizeWords) {
                chunks.add(paragraph);
            } else {
                chunks.addAll(slidingWindowSplit(paragraph));
            }
        }
        return chunks;
    }

    private List<String> slidingWindowSplit(String text) {
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();

        int step = Math.max(1, chunkSizeWords - overlapWords);
        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(start + chunkSizeWords, words.length);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(words[i]).append(' ');
            }
            chunks.add(sb.toString().trim());
            if (end == words.length) break;
        }
        return chunks;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrotto durante l'attesa tra chunk", e);
        }
    }
}