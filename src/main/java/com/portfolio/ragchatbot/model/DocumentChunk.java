package com.portfolio.ragchatbot.model;

public class DocumentChunk {

    private Long id;
    private String source;
    private String content;
    private float[] embedding;
    private double similarity; // valorizzato solo nei risultati di retrieval

    public DocumentChunk() {
    }

    public DocumentChunk(String source, String content, float[] embedding) {
        this.source = source;
        this.content = content;
        this.embedding = embedding;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(float[] embedding) {
        this.embedding = embedding;
    }

    public double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(double similarity) {
        this.similarity = similarity;
    }
}
