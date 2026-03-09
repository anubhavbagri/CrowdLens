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

/**
 * AI analysis engine powered by Spring AI's ChatModel interface.
 * Model-agnostic: swap OpenAI → Anthropic → Gemini → Ollama via config.
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
     * Analyzes social media posts and produces a structured SearchResponse.
     */
    @CircuitBreaker(name = "openAi", fallbackMethod = "analyzeFallback")
    public AnalysisResult analyze(List<SocialPostDto> posts, String query) {
        if (posts.isEmpty()) {
            log.warn("No posts to analyze for query: '{}'", query);
            return AnalysisResult.empty(query);
        }

        String prompt = promptBuilder.buildPrompt(posts, query);
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
            log.error("AI analysis failed for query '{}': {} — {}", query, e.getClass().getSimpleName(),
                    e.getMessage());

            // Provide specific error guidance
            if (e.getMessage() != null && (e.getMessage().contains("authentication") ||
                    e.getMessage().contains("401") ||
                    e.getMessage().contains("Unauthorized") ||
                    e.getMessage().contains("invalid_api_key"))) {
                log.error("🔑 OpenAI API key appears to be INVALID or EXPIRED. " +
                        "Check OPENAI_API_KEY in your .env file.");
            } else if (e.getMessage() != null && (e.getMessage().contains("429") ||
                    e.getMessage().contains("rate_limit") ||
                    e.getMessage().contains("quota"))) {
                log.error("⚠️ OpenAI rate limit or quota exceeded. " +
                        "Check your usage at https://platform.openai.com/usage");
            }

            throw e; // Let circuit breaker handle it
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

    public record HealthStatus(boolean healthy, String message, String hint) {
    }

    private AnalysisResult parseAiResponse(String aiOutput, String query) {
        try {
            // Strip markdown code fences if present
            String json = aiOutput;
            if (json.startsWith("```")) {
                json = json.replaceAll("^```\\w*\\n?", "").replaceAll("\\n?```$", "");
            }
            json = json.trim();

            JsonNode root = objectMapper.readTree(json);

            int overallScore = root.path("overallScore").asInt(50);
            String overallVerdict = root.path("overallVerdict").asText("Mixed");
            String verdictSummary = root.path("verdictSummary").asText("");

            List<SearchResponse.CategoryAnalysis> categories = parseCategories(root.path("categories"));
            List<SearchResponse.Testimonial> testimonials = parseTestimonials(root.path("testimonials"));
            SearchResponse.PersonaAnalysis personaAnalysis = parsePersonaAnalysis(root.path("personaAnalysis"));

            log.info("AI analysis parsed — score: {}, verdict: '{}', categories: {}, testimonials: {}",
                    overallScore, overallVerdict, categories.size(), testimonials.size());

            return new AnalysisResult(
                    overallScore, overallVerdict, verdictSummary,
                    categories, testimonials, personaAnalysis, json);

        } catch (Exception e) {
            log.error("Failed to parse AI response for query '{}': {}", query, e.getMessage());
            log.debug("Unparseable AI output:\n{}", aiOutput);
            return AnalysisResult.error(query, "Failed to parse AI response");
        }
    }

    private List<SearchResponse.CategoryAnalysis> parseCategories(JsonNode categoriesNode) {
        if (categoriesNode.isMissingNode() || !categoriesNode.isArray())
            return Collections.emptyList();

        try {
            return objectMapper.readValue(
                    categoriesNode.toString(),
                    new TypeReference<List<Map<String, Object>>>() {
                    }).stream().map(map -> SearchResponse.CategoryAnalysis.builder()
                            .name((String) map.get("name"))
                            .rating((String) map.get("rating"))
                            .summary((String) map.get("summary"))
                            .highlights(map.containsKey("highlights")
                                    ? objectMapper.convertValue(map.get("highlights"),
                                            new TypeReference<List<String>>() {
                                            })
                                    : Collections.emptyList())
                            .build())
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse categories: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<SearchResponse.Testimonial> parseTestimonials(JsonNode testimonialsNode) {
        if (testimonialsNode.isMissingNode() || !testimonialsNode.isArray())
            return Collections.emptyList();

        try {
            return objectMapper.readValue(
                    testimonialsNode.toString(),
                    new TypeReference<List<Map<String, String>>>() {
                    }).stream().map(map -> SearchResponse.Testimonial.builder()
                            .text(map.get("text"))
                            .sentiment(map.get("sentiment"))
                            .source(map.get("source"))
                            .platform(map.getOrDefault("platform", "reddit"))
                            .permalink(map.get("permalink"))
                            .build())
                    .toList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse testimonials: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private SearchResponse.PersonaAnalysis parsePersonaAnalysis(JsonNode personaNode) {
        if (personaNode.isMissingNode())
            return null;

        String question = personaNode.path("question").asText("Is this right for you?");
        List<SearchResponse.PersonaFit> fits;

        try {
            fits = objectMapper.readValue(
                    personaNode.path("fits").toString(),
                    new TypeReference<List<Map<String, String>>>() {
                    }).stream().map(map -> SearchResponse.PersonaFit.builder()
                            .persona(map.get("persona"))
                            .verdict(map.get("verdict"))
                            .reason(map.get("reason"))
                            .build())
                    .toList();
        } catch (Exception e) {
            fits = Collections.emptyList();
        }

        return SearchResponse.PersonaAnalysis.builder()
                .question(question)
                .fits(fits)
                .build();
    }

    /**
     * Structured result from AI analysis.
     */
    public record AnalysisResult(
            int overallScore,
            String overallVerdict,
            String verdictSummary,
            List<SearchResponse.CategoryAnalysis> categories,
            List<SearchResponse.Testimonial> testimonials,
            SearchResponse.PersonaAnalysis personaAnalysis,
            String rawJson) {
        public static AnalysisResult empty(String query) {
            return new AnalysisResult(
                    0, "No Data",
                    "No social media posts found for this query.",
                    Collections.emptyList(), Collections.emptyList(), null, "{}");
        }

        public static AnalysisResult error(String query, String errorMessage) {
            return new AnalysisResult(
                    0, "AI Unavailable",
                    "AI analysis failed: " + errorMessage
                            + ". Reddit data was collected successfully — retry may work.",
                    Collections.emptyList(), Collections.emptyList(), null, "{}");
        }
    }
}
