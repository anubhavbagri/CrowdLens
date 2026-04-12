package com.crowdlens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Lightweight AI service backed by Google Gemini (AI Studio REST API).
 *
 * Calls the Gemini generateContent endpoint directly via WebClient —
 * no Spring AI Gemini starter needed, no Vertex AI / GCP credentials required.
 *
 * Intended for tasks where:
 *   - Low cost / fast response is prioritised over absolute accuracy
 *   - We want to offload work from OpenAI to avoid rate-limit pressure
 *
 * Current assignments:
 *   - Loading-screen hint generation (LoadingHintsService)
 *
 * Error handling:
 *   A Resilience4j circuit breaker ("gemini") wraps every call.
 *   On repeated failures the breaker opens and the fallback returns null,
 *   allowing the caller to gracefully degrade.
 */
@Slf4j
@Service
public class GeminiChatService {

    private static final String GEMINI_API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public GeminiChatService(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${GEMINI_MODEL:gemini-2.0-flash}") String model) {
        this.webClient    = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.apiKey       = apiKey;
        this.model        = model;
    }

    /**
     * Sends a prompt to Gemini and returns the raw text response.
     *
     * @param prompt The plain-text prompt to send
     * @return Gemini's text response, or null if the call fails / key not configured
     */
    @CircuitBreaker(name = "gemini", fallbackMethod = "callFallback")
    public String call(String prompt) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("GEMINI_API_KEY not configured — skipping Gemini call");
            return null;
        }

        log.debug("Sending prompt to Gemini ({} chars)", prompt.length());

        // Build request body: { "contents": [{ "parts": [{ "text": "..." }] }] }
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        String url = GEMINI_API_BASE + model + ":generateContent?key=" + apiKey;

        String raw = webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        if (raw == null || raw.isBlank()) {
            log.warn("Gemini returned empty response");
            return null;
        }

        // Parse: candidates[0].content.parts[0].text
        JsonNode root = objectMapper.readTree(raw);
        String output = root
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text")
                .asText(null);

        log.debug("Gemini response received ({} chars)", output != null ? output.length() : 0);
        return output;
    }

    @SuppressWarnings("unused")
    private String callFallback(String prompt, Throwable t) {
        log.warn("Gemini circuit breaker OPEN: {} — {}", t.getClass().getSimpleName(), t.getMessage());
        return null;
    }

    /**
     * Health check — sends a minimal prompt to verify Gemini connectivity.
     */
    public boolean isHealthy() {
        try {
            String response = call("Reply with exactly: OK");
            boolean healthy = response != null && response.contains("OK");
            log.info("Gemini health check {}", healthy ? "PASSED" : "FAILED (unexpected response)");
            return healthy;
        } catch (Exception e) {
            log.warn("Gemini health check FAILED: {}", e.getMessage());
            return false;
        }
    }
}
