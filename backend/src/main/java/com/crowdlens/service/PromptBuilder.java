package com.crowdlens.service;

import com.crowdlens.model.dto.SocialPostDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds structured prompts for AI analysis based on social media posts.
 * Detects query type automatically and selects relevant categories.
 */
@Slf4j
@Component
public class PromptBuilder {

    public enum QueryType {
        PRODUCT,
        SERVICE,
        EXPERIENCE,
        COMPARISON,
        GENERAL
    }

    /**
     * Builds the full analysis prompt for the AI model.
     */
    public String buildPrompt(List<SocialPostDto> posts, String query) {
        QueryType queryType = detectQueryType(query);
        String categories = getCategoriesForType(queryType);
        String postsContext = formatPosts(posts);

        return """
                You are an expert analyst that synthesizes real user opinions from social media into structured, actionable analysis.

                QUERY: "%s"
                QUERY TYPE: %s

                SOCIAL MEDIA POSTS (%d posts):
                %s

                INSTRUCTIONS:
                Analyze ALL the posts above and produce a JSON response with this EXACT structure:

                {
                  "overallScore": <0-100 integer>,
                  "overallVerdict": "<one of: Excellent, Good, Fair, Poor, Mixed>",
                  "verdictSummary": "<2-3 sentence summary of the overall sentiment and key takeaways>",
                  "categories": [
                    {
                      "name": "<category name>",
                      "rating": "<one of: Excellent, Good, Fair, Poor>",
                      "summary": "<1-2 paragraph summary for this category>",
                      "highlights": ["<key point 1>", "<key point 2>", "<key point 3>"]
                    }
                  ],
                  "testimonials": [
                    {
                      "text": "<exact or near-exact quote from a post>",
                      "sentiment": "<positive, neutral, or negative>",
                      "source": "<subreddit or source>",
                      "platform": "reddit",
                      "permalink": "<permalink if available>"
                    }
                  ],
                  "personaAnalysis": {
                    "question": "Is this right for you?",
                    "fits": [
                      {
                        "persona": "<user type, e.g. 'Budget-conscious buyer'>",
                        "verdict": "<Great fit, Good fit, Not ideal>",
                        "reason": "<1 sentence explanation>"
                      }
                    ]
                  }
                }

                CATEGORIES TO EVALUATE: %s

                RULES:
                1. Score from 0-100 based on overall sentiment across ALL posts
                2. Select 5-8 of the most representative testimonials with a mix of sentiments
                3. Generate 3-5 persona fits covering different user types
                4. Categories should have 3-5 highlights each
                5. Be objective — reflect what users ACTUALLY say, don't add your own opinion
                6. If posts are mixed, reflect that honestly in the verdict
                7. Return ONLY the JSON object, no markdown, no explanation
                """
                .formatted(query, queryType, posts.size(), postsContext, categories);
    }

    /**
     * Auto-detects the query type based on keywords.
     */
    public QueryType detectQueryType(String query) {
        String lower = query.toLowerCase();

        if (lower.contains(" vs ") || lower.contains(" versus ") || lower.contains(" or ")
                || lower.contains("compare") || lower.contains("comparison")) {
            return QueryType.COMPARISON;
        }

        if (lower.contains("supplement") || lower.contains("product") || lower.contains("phone")
                || lower.contains("laptop") || lower.contains("headphone") || lower.contains("camera")
                || lower.contains("shoe") || lower.contains("mattress") || lower.contains("keyboard")
                || lower.contains("monitor") || lower.contains("tablet")) {
            return QueryType.PRODUCT;
        }

        if (lower.contains("service") || lower.contains("subscription") || lower.contains("insurance")
                || lower.contains("bank") || lower.contains("vpn") || lower.contains("hosting")
                || lower.contains("provider") || lower.contains("plan")) {
            return QueryType.SERVICE;
        }

        if (lower.contains("experience") || lower.contains("trip") || lower.contains("visit")
                || lower.contains("restaurant") || lower.contains("hotel") || lower.contains("course")
                || lower.contains("bootcamp") || lower.contains("university")) {
            return QueryType.EXPERIENCE;
        }

        return QueryType.GENERAL;
    }

    /**
     * Returns relevant categories based on query type.
     */
    public String getCategoriesForType(QueryType type) {
        return switch (type) {
            case PRODUCT -> "Efficacy, Quality, Value for Money, Safety/Side Effects, Ease of Use, Durability";
            case SERVICE -> "Reliability, Customer Support, Value for Money, Features, Ease of Use, Trustworthiness";
            case EXPERIENCE ->
                "Overall Experience, Value for Money, Quality, Atmosphere, Accessibility, Would Recommend";
            case COMPARISON ->
                "Performance, Value for Money, Features, Reliability, User Experience, Community Preference";
            case GENERAL -> "Overall Sentiment, Quality, Value, Pros, Cons, Recommendations";
        };
    }

    private String formatPosts(List<SocialPostDto> posts) {
        return posts.stream()
                .limit(50) // Cap at 50 posts to stay within context window
                .map(post -> """
                        ---
                        [%s] Score: %d | Source: %s
                        Title: %s
                        Body: %s
                        Permalink: %s
                        """.formatted(
                        post.platform(),
                        post.score(),
                        post.source(),
                        post.title(),
                        truncate(post.body(), 500),
                        post.permalink()))
                .collect(Collectors.joining("\n"));
    }

    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
