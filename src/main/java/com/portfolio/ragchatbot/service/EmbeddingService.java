package com.portfolio.ragchatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class EmbeddingService {

    private static final int MAX_RETRIES = 4;
    private static final long INITIAL_BACKOFF_MS = 5000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${voyage.api-key}")
    private String apiKey;

    @Value("${voyage.base-url}")
    private String baseUrl;

    @Value("${voyage.model}")
    private String model;

    public float[] embedDocument(String text) {
        return embed(text, "document");
    }

    public float[] embedQuery(String text) {
        return embed(text, "query");
    }

    private float[] embed(String text, String inputType) {
        int attempt = 0;
        long backoff = INITIAL_BACKOFF_MS;

        while (true) {
            try {
                return doEmbed(text, inputType);
            } catch (RateLimitException e) {
                attempt++;
                if (attempt > MAX_RETRIES) {
                    throw new RuntimeException(
                            "Limite di richieste Voyage AI superato anche dopo " + MAX_RETRIES + " tentativi.", e);
                }
                System.out.println("Rate limit Voyage AI, riprovo tra " + (backoff / 1000) + "s (tentativo "
                        + attempt + "/" + MAX_RETRIES + ")...");
                sleep(backoff);
                backoff *= 2;
            } catch (Exception e) {
                throw new RuntimeException("Errore durante la generazione dell'embedding", e);
            }
        }
    }

    private float[] doEmbed(String text, String inputType) throws Exception {
        String requestBody = mapper.writeValueAsString(new EmbedRequest(model, new String[]{text}, inputType));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) {
            throw new RateLimitException(response.body());
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Voyage AI ha risposto con status " + response.statusCode()
                    + ": " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode vectorNode = root.path("data").get(0).path("embedding");

        float[] embedding = new float[vectorNode.size()];
        for (int i = 0; i < vectorNode.size(); i++) {
            embedding[i] = (float) vectorNode.get(i).asDouble();
        }
        return embedding;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrotto durante l'attesa per il rate limit", e);
        }
    }

    private static class RateLimitException extends RuntimeException {
        RateLimitException(String message) {
            super(message);
        }
    }

    private record EmbedRequest(String model, String[] input, String input_type) {
    }
}