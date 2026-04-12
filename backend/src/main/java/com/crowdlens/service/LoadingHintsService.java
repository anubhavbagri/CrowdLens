package com.crowdlens.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Generates contextual, AI-driven loading-screen hints for a given product query.
 *
 * Delegates entirely to Gemini — no hardcoded category maps or static fallbacks.
 * If Gemini is unavailable (key not configured, circuit open, parse error),
 * returns null so the controller can respond with an empty hint list.
 * The frontend is responsible for its own generic fallback display.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoadingHintsService {

    private final GeminiChatService geminiChatService;
    private final ObjectMapper objectMapper;

    private static final String PROMPT_TEMPLATE = """
            You are helping build a loading screen for a product review analyzer.
            A user just searched for: "%s"

            Generate exactly 5 short, specific loading messages (8-12 words each) that describe what the AI is analyzing about this product.
            Each message must:
            - Start with an active verb: Analyzing, Checking, Scanning, Evaluating, Reading, or Examining
            - Be specific to the actual product category inferred from the query — not generic
            - End with "…"
            - Feel like a live data-gathering status update

            Return ONLY a valid JSON array of 5 strings. No markdown, no explanation, no extra keys.
            Example for "moisturizer":
            ["Analyzing hydration and skin comfort feedback…","Checking for fragrance-related concerns…","Scanning oily-skin user experiences…","Evaluating long-term moisturization results…","Reading dermatologist community posts…"]
            """;

    /**
     * Calls Gemini to generate dynamic, query-specific loading hints.
     *
     * @param query The product search query
     * @return A list of 3–5 hint strings, or null if Gemini is unavailable or parsing fails.
     *         The caller should treat null as "no hints available" and respond accordingly.
     */
    public List<String> generateWithGemini(String query) {
        String prompt = PROMPT_TEMPLATE.formatted(query);
        String raw;
        try {
            raw = geminiChatService.call(prompt);
        } catch (Exception e) {
            log.warn("Gemini call failed for query '{}': {}", query, e.getMessage());
            return null;
        }

        if (raw == null || raw.isBlank()) {
            log.debug("Gemini returned null/empty for loading hints query '{}'", query);
            return null;
        }

        try {
            // Strip markdown code fences if Gemini wraps the response with ```json ... ```
            String cleaned = raw.replaceAll("(?s)```[a-z]*\\n?", "").replaceAll("```", "").trim();
            List<String> hints = objectMapper.readValue(cleaned, new TypeReference<>() {});
            if (hints.size() < 3) {
                log.warn("Gemini returned too few loading hints ({}) for query '{}'", hints.size(), query);
                return null;
            }
            log.debug("Gemini generated {} loading hints for query '{}'", hints.size(), query);
            return hints.subList(0, Math.min(hints.size(), 5));
        } catch (Exception e) {
            log.warn("Failed to parse Gemini loading hints for '{}': {}", query, e.getMessage());
            return null;
        }
    }
}
