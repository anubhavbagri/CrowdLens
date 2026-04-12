package com.crowdlens.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Lightweight AI service backed by Google Gemini via Spring AI's Google GenAI
 * integration.
 *
 * Uses Gemini Developer API key from Google AI Studio —
 * no GCP project, no Vertex AI, no service accounts required.
 * Configured via spring.ai.google.genai.api-key in application.yml.
 *
 * Intended for tasks where:
 * - Low cost / fast response is prioritised over absolute accuracy
 * - We want to offload work from OpenAI to avoid rate-limit pressure
 *
 * Current assignments:
 * - Loading-screen hint generation (LoadingHintsService)
 *
 * Error handling:
 * A Resilience4j circuit breaker ("gemini") wraps every call.
 * On repeated failures the breaker opens and the fallback returns null,
 * allowing the caller to gracefully degrade.
 */
@Slf4j
@Service
public class GeminiChatService {

    private final ChatModel geminiChatModel;

    public GeminiChatService(@Qualifier("googleGenAiChatModel") ChatModel geminiChatModel) {
        this.geminiChatModel = geminiChatModel;
    }

    /**
     * Sends a prompt to Gemini and returns the raw text response.
     *
     * @param prompt The plain-text prompt to send
     * @return Gemini's text response, or null if the call fails
     */
    @CircuitBreaker(name = "gemini", fallbackMethod = "callFallback")
    public String call(String prompt) {
        log.debug("Sending prompt to Gemini ({} chars)", prompt.length());

        ChatResponse response = geminiChatModel.call(new Prompt(prompt));
        String output = response.getResult().getOutput().getText();

        log.debug("Gemini response received ({} chars)", output != null ? output.length() : 0);
        return output;
    }

    @SuppressWarnings("unused")
    private String callFallback(String prompt, Throwable t) {
        log.warn("Gemini call failed: {} — {}", t.getClass().getSimpleName(), t.getMessage());
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
