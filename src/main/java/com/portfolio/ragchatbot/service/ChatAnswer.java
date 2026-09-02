package com.portfolio.ragchatbot.service;

import java.util.List;

public record ChatAnswer(String answer, List<SourceCitation> sources, DebugInfo debug) {

    public record SourceCitation(String source, String snippet, double similarity) {
    }

    public record DebugInfo(String embeddingModel, String generationModel, int topK,
                             long retrievalTimeMs, long generationTimeMs) {
    }
}
