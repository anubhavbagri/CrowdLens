package com.crowdlens.service;

import com.crowdlens.model.dto.SearchResponse;
import com.crowdlens.model.dto.SocialPostDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI analysis engine powered by Spring AI's ChatModel interface.
 * Model-agnostic: swap OpenAI → Anthropic → Gemini → Ollama via config.
 *
 * Parses the new dynamic-metric response shape introduced in V2.
 */
@Slf4j
@Service
public class AIAnalysisEngine {

    private final ChatModel chatModel;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;

    public AIAnalysisEngine(ChatModel chatModel, PromptBuilder promptBuilder, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * Analyzes social media posts and produces a structured AnalysisResult.
     */
    @CircuitBreaker(name = "openAi", fallbackMethod = "analyzeFallback")
    public AnalysisResult analyze(List<SocialPostDto> posts, String query) {
        if (posts.isEmpty()) {
            log.warn("No posts to analyze for query: '{}'", query);
            return AnalysisResult.empty(query);
        }

        // Collect candidate image URLs from posts (up to 10, non-null only)
        List<String> candidateImageUrls = posts.stream()
                .map(SocialPostDto::imageUrl)
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .limit(10)
                .collect(Collectors.toList());

        String prompt = promptBuilder.buildPrompt(posts, query, candidateImageUrls);
        log.info("Sending analysis request to AI for query '{}' ({} posts, prompt length: {} chars)",
                query, posts.size(), prompt.length());

        try {
            ChatResponse response = chatModel.call(new Prompt(prompt));
            String aiOutput = response.getResult().getOutput().getText();

            log.info("AI response received for query '{}' — length: {} chars", query,
                    aiOutput != null ? aiOutput.length() : 0);
            log.debug("AI raw response:\n{}", aiOutput);

            return parseAiResponse(aiOutput, query);

        } catch (Exception e) {
            log.error("AI analysis failed for query '{}': {} — {}", query, e.getClass().getSimpleName(), e.getMessage());

            if (e.getMessage() != null && (
                    e.getMessage().contains("authentication") ||
                    e.getMessage().contains("401") ||
                    e.getMessage().contains("Unauthorized") ||
                    e.getMessage().contains("invalid_api_key"))) {
                log.error("🔑 OpenAI API key appears INVALID or EXPIRED. Check OPENAI_API_KEY in your .env file.");
            } else if (e.getMessage() != null && (
                    e.getMessage().contains("429") ||
                    e.getMessage().contains("rate_limit") ||
                    e.getMessage().contains("quota"))) {
                log.error("⚠️ OpenAI rate limit or quota exceeded. Check usage at https://platform.openai.com/usage");
            }

            throw e;
        }
    }

    @SuppressWarnings("unused")
    private AnalysisResult analyzeFallback(List<SocialPostDto> posts, String query, Throwable t) {
        log.error("AI analysis circuit breaker OPEN for query '{}': {} — {}",
                query, t.getClass().getSimpleName(), t.getMessage());
        return AnalysisResult.error(query, t.getMessage());
    }

    /**
     * Health check: sends a minimal prompt to verify AI connectivity.
     */
    public HealthStatus healthCheck() {
        try {
            ChatResponse response = chatModel.call(new Prompt("Reply with exactly: OK"));
            String output = response.getResult().getOutput().getText();
            if (output != null && !output.isBlank()) {
                log.info("AI health check passed — model responded: '{}'", output.trim());
                return new HealthStatus(true, "AI model is responsive", null);
            }
            return new HealthStatus(false, "AI model returned empty response", null);
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            log.error("AI health check FAILED: {} — {}", e.getClass().getSimpleName(), errorMsg);

            if (errorMsg != null && (errorMsg.contains("authentication") ||
                    errorMsg.contains("401") || errorMsg.contains("invalid_api_key"))) {
                return new HealthStatus(false, "Invalid API key", "Check OPENAI_API_KEY in .env");
            } else if (errorMsg != null && (errorMsg.contains("429") || errorMsg.contains("quota"))) {
                return new HealthStatus(false, "Rate limit or quota exceeded",
                        "Check usage at https://platform.openai.com/usage");
            }
            return new HealthStatus(false, e.getClass().getSimpleName() + ": " + errorMsg, null);
        }
    }

    public record HealthStatus(boolean healthy, String message, String hint) {}

    /**
     * Lightweight call — no Reddit posts needed.
     * Asks the AI to suggest 3 direct competitors for a product in a given category/subcategory
     * and estimate their community scores from general knowledge.
     *
     * Used as a fallback by CompetitorService when no entries exist in SQLite.
     */
    public List<AnalysisResult.CompetitorSeed> suggestCompetitors(
            String productQuery, String category, String subCategory) {

        String prompt = """
                You are a product expert with knowledge of consumer sentiment on Reddit.
                
                Product: "%s"
                Category: %s
                Sub-category: %s
                
                Suggest exactly 3 products that directly compete with the above product
                in the SAME category AND sub-category. Do NOT include the product itself.
                For each, estimate a Reddit community score (0–100) based on general public opinion.
                
                Return ONLY a JSON array. No explanation, no markdown:
                [
                  { "name": "<product name>", "estimatedScore": <0-100> },
                  { "name": "<product name>", "estimatedScore": <0-100> },
                  { "name": "<product name>", "estimatedScore": <0-100> }
                ]
                """.formatted(productQuery,
                category != null ? category : "Unknown",
                subCategory != null ? subCategory : "Unknown");

        try {
            ChatResponse response = chatModel.call(new Prompt(prompt));
            String output = response.getResult().getOutput().getText();
            if (output == null || output.isBlank()) return Collections.emptyList();

            // Strip markdown fences if present
            String json = output;
            if (json.contains("```")) {
                json = json.replaceAll("(?s)```\\w*\\n?", "").replaceAll("```", "").trim();
            }
            JsonNode root = objectMapper.readTree(json);
            return parseCompetitorSeeds(root);
        } catch (Exception e) {
            log.warn("suggestCompetitors failed for '{}': {}", productQuery, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─── Parsing ────────────────────────────────────────────────────────────────

    private AnalysisResult parseAiResponse(String aiOutput, String query) {
        try {
            // Strip markdown code fences if present
            String json = aiOutput;
            if (json != null && json.contains("```")) {
                json = json.replaceAll("(?s)```\\w*\\n?", "").replaceAll("```", "");
            }
            if (json == null) throw new IllegalStateException("AI returned null output");
            json = json.trim();

            JsonNode root = objectMapper.readTree(json);

            String productCategory    = root.path("productCategory").asText(null);
            String productSubCategory = root.path("productSubCategory").asText(null);
            int overallScore          = root.path("overallScore").asInt(50);
            String verdictSentence    = root.path("verdictSentence").asText("");

            List<SearchResponse.Metric>          metrics          = parseMetrics(root.path("metrics"));
            List<String>                         positives        = parseStringArray(root.path("positives"));
            List<String>                         complaints       = parseStringArray(root.path("complaints"));
            List<String>                         bestFor          = parseStringArray(root.path("bestFor"));
            List<String>                         avoid            = parseStringArray(root.path("avoid"));
            List<SearchResponse.EvidenceSnippet> evidenceSnippets = parseEvidenceSnippets(root.path("evidenceSnippets"));
            List<AnalysisResult.CompetitorSeed>  competitorSeeds  = parseCompetitorSeeds(root.path("competitorSuggestions"));
            String                               productImageUrl  = root.path("productImageUrl").isNull() ? null
                    : root.path("productImageUrl").asText(null);

            log.info("AI parsed — category: '{}', subcategory: '{}', score: {}, metrics: {}, competitors: {}, imageUrl: {}",
                    productCategory, productSubCategory, overallScore, metrics.size(), competitorSeeds.size(),
                    productImageUrl != null ? "found" : "none");

            return new AnalysisResult(
                    productCategory, productSubCategory, overallScore, verdictSentence,
                    metrics, positives, complaints, bestFor, avoid, evidenceSnippets, json, competitorSeeds, productImageUrl
            );

        } catch (Exception e) {
            log.error("Failed to parse AI response for query '{}': {}", query, e.getMessage());
            log.debug("Unparseable AI output:\n{}", aiOutput);
            return AnalysisResult.error(query, "Failed to parse AI response");
        }
    }

    private List<SearchResponse.Metric> parseMetrics(JsonNode metricsNode) {
        if (metricsNode.isMissingNode() || !metricsNode.isArray()) return Collections.emptyList();
        try {
            return objectMapper.readValue(
                    metricsNode.toString(),
                    new TypeReference<List<Map<String, Object>>>() {}
            ).stream().map(map -> SearchResponse.Metric.builder()
                    .label((String) map.get("label"))
                    .score(toDouble(map.get("score")))
                    .explanation((String) map.get("explanation"))
                    .build()
            ).toList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse metrics: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> parseStringArray(JsonNode node) {
        if (node.isMissingNode() || !node.isArray()) return Collections.emptyList();
        try {
            return objectMapper.readValue(node.toString(), new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse string array: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchResponse.EvidenceSnippet> parseEvidenceSnippets(JsonNode snippetsNode) {
        if (snippetsNode.isMissingNode() || !snippetsNode.isArray()) return Collections.emptyList();
        try {
            return objectMapper.readValue(
                    snippetsNode.toString(),
                    new TypeReference<List<Map<String, String>>>() {}
            ).stream().map(map -> SearchResponse.EvidenceSnippet.builder()
                    .text(map.get("text"))
                    .source(map.get("source"))
                    .permalink(map.get("permalink"))
                    .build()
            ).toList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse evidenceSnippets: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private List<AnalysisResult.CompetitorSeed> parseCompetitorSeeds(JsonNode node) {
        if (node.isMissingNode() || !node.isArray()) return Collections.emptyList();
        try {
            return objectMapper.readValue(
                    node.toString(),
                    new TypeReference<List<Map<String, Object>>>() {}
            ).stream()
                    .filter(map -> map.get("name") != null)
                    .map(map -> new AnalysisResult.CompetitorSeed(
                            (String) map.get("name"),
                            (int) toDouble(map.get("estimatedScore"))
                    ))
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse competitorSuggestions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─── Result record ──────────────────────────────────────────────────────────

    /**
     * Structured result from AI analysis — maps 1:1 to the new SearchResponse shape.
     */
    public record AnalysisResult(
            String productCategory,
            String productSubCategory,
            int overallScore,
            String verdictSentence,
            List<SearchResponse.Metric> metrics,
            List<String> positives,
            List<String> complaints,
            List<String> bestFor,
            List<String> avoid,
            List<SearchResponse.EvidenceSnippet> evidenceSnippets,
            String rawJson,
            List<CompetitorSeed> competitorSeeds,
            /** AI-validated product image URL from Reddit posts. Null if none were suitable. */
            String productImageUrl
    ) {
        /** AI-suggested competitor: name + estimated community score as placeholder. */
        public record CompetitorSeed(String name, int estimatedScore) {}

        public static AnalysisResult empty(String query) {
            return new AnalysisResult(
                    null, null, 0,
                    "No social media posts found for this query — try a different search term.",
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "{}",
                    Collections.emptyList(), null
            );
        }

        public static AnalysisResult error(String query, String errorMessage) {
            return new AnalysisResult(
                    null, null, 0,
                    "AI analysis temporarily unavailable — Reddit data was collected. Retry shortly.",
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), "{}",
                    Collections.emptyList(), null
            );
        }
    }
}
