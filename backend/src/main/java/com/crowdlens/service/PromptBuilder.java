package com.crowdlens.service;

import com.crowdlens.model.dto.SocialPostDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds structured prompts for AI analysis.
 *
 * Core principle: metrics are NEVER hardcoded.
 * The AI is responsible for:
 * 1. Classifying the product category
 * 2. Extracting discussion themes from posts
 * 3. Ranking and selecting exactly 4 metrics
 * 4. Scoring each metric based on sentiment evidence
 */
@Slf4j
@Component
public class PromptBuilder {

    /**
     * Builds the full analysis prompt for the AI model.
     */
    public String buildPrompt(List<SocialPostDto> posts, String query) {
        String postsContext = formatPosts(posts);
        log.debug("Building prompt for query '{}' with {} posts", query, posts.size());

        return """
                You are CrowdLens — an AI that turns real community opinions into structured product verdicts.
                Your job: read all the posts below and produce a clean, honest JSON analysis.

                PRODUCT QUERY: "%s"
                TOTAL POSTS + COMMENTS: %d

                ─── COMMUNITY POSTS ───
                %s
                ─── END OF POSTS ───

                ═══ YOUR TASK ═══

                Step 1 — Classify the product:
                Detect the product category (e.g. Grooming, Audio, Footwear, Kitchen Appliance, Laptop, Skincare, Motorcycle, Supplement)
                Detect the sub-category (e.g. Electric Trimmer, Bluetooth Speaker, Running Shoe, Pressure Cooker)

                Step 2 — Extract discussion themes:
                Read all posts. Identify the recurring topics people actually discuss.
                Examples of raw themes: "battery lasts long", "painful on skin", "blade pulls", "feels cheap", "good value"

                Step 3 — Select exactly 4 metrics:
                Pick the 4 themes that are:
                - Most frequently mentioned
                - Most relevant for buying decisions in this category
                - Not redundant with each other
                - Specific to this category (don't use "Portability" for a pressure cooker)

                Theme-to-metric mapping logic:
                "irritation", "cuts skin", "comfortable on face" → Skin Comfort
                "battery lasts 2 weeks", "charges fast" → Battery Life
                "does not cut thick beard" → Cutting Performance
                "feels cheap", "plastic body", "good grip" → Build Quality
                "sound is loud", "bass heavy", "clear highs" → Sound Quality
                "fits perfectly", "runs small", "size accurate" → Fit Accuracy
                "motor is powerful", "grinds fine", "struggles with hard ingredients" → Motor Power
                "easy to wash", "difficult to clean parts" → Ease of Cleaning
                Use your judgment to name metrics cleanly and professionally.

                Step 4 — Score each metric:
                Score 0.0–10.0 based on sentiment in the posts for that theme.
                Scoring guide:
                9.0–10.0 = almost universally praised
                7.0–8.9  = mostly positive with minor complaints
                5.0–6.9  = genuinely mixed
                3.0–4.9  = more complaints than praise
                0.0–2.9  = severe criticism

                Step 5 — Blended overall score (0–100):
                Use a blended approach, not just flat sentiment:
                - Sentiment balance (are most opinions positive?)
                - Repetition strength (how often do themes appear?)
                - Decision importance (are complaints deal-breakers?)
                - Confidence (enough volume of discussion?)
                Example: high praise but only 3 posts → cap at 75. Mixed but minor complaints on a clear use case → 65–70.

                Step 6 — Verdict sentence:
                Write ONE sentence that captures the product's core trade-off.
                Format: "Good buy if [strength], but [main weakness]."
                Or: "Excellent choice for [persona], though [caveat]."
                BAD: "Mixed reviews." or "Good product overall."
                GOOD: "Strong daily-use trimmer with excellent battery life and comfortable trimming, but build quality feels plasticky for the price."

                Step 7 — Positives and complaints:
                Extract 3–5 most repeated positive themes. Write as short punchy phrases (not full sentences).
                Extract 3–5 most repeated complaints. Same format.

                Step 8 — Best for / Avoid:
                List 2–4 user personas who would love this product.
                List 2–4 user personas who should avoid it.
                Write as short descriptive phrases.

                Step 9 — Evidence snippets:
                Pick 4–6 of the most representative posts/comments.
                Paraphrase them into short, readable snippets (1–2 sentences max).
                Include the subreddit source and permalink.

                Step 10 — Competitor suggestions:
                Suggest exactly 3 similar products that compete directly in the same category AND sub-category.
                Do NOT include the queried product itself.
                For each competitor, provide an estimated Reddit community score (0–100) based on your general knowledge of public opinion.
                These are placeholder scores — they will be refined once users search for those products.

                ═══ OUTPUT FORMAT ═══
                Return ONLY this JSON. No markdown. No explanation. No extra keys.

                {
                  "productCategory": "<e.g. Grooming>",
                  "productSubCategory": "<e.g. Electric Trimmer>",
                  "overallScore": <0-100 integer>,
                  "verdictSentence": "<single crafted sentence>",
                  "metrics": [
                    { "label": "<metric name>", "score": <0.0-10.0>, "explanation": "<1 sentence from post evidence>" },
                    { "label": "<metric name>", "score": <0.0-10.0>, "explanation": "<1 sentence from post evidence>" },
                    { "label": "<metric name>", "score": <0.0-10.0>, "explanation": "<1 sentence from post evidence>" },
                    { "label": "<metric name>", "score": <0.0-10.0>, "explanation": "<1 sentence from post evidence>" }
                  ],
                  "positives": ["<theme 1>", "<theme 2>", "<theme 3>"],
                  "complaints": ["<complaint 1>", "<complaint 2>", "<complaint 3>"],
                  "bestFor": ["<persona 1>", "<persona 2>", "<persona 3>"],
                  "avoid": ["<persona 1>", "<persona 2>"],
                  "evidenceSnippets": [
                    { "text": "<paraphrased snippet>", "source": "<r/subreddit>", "permalink": "<url>" },
                    { "text": "<paraphrased snippet>", "source": "<r/subreddit>", "permalink": "<url>" }
                  ],
                  "competitorSuggestions": [
                    { "name": "<competitor product name>", "estimatedScore": <0-100 integer> },
                    { "name": "<competitor product name>", "estimatedScore": <0-100 integer> },
                    { "name": "<competitor product name>", "estimatedScore": <0-100 integer> }
                  ]
                }
                """.formatted(query, posts.size(), postsContext);
    }

    private String formatPosts(List<SocialPostDto> posts) {
        return posts.stream()
                .limit(60) // Stay within context window
                .map(post -> """
                        [%s | score:%d | %s]
                        %s
                        %s
                        <%s>
                        """.formatted(
                        post.platform(),
                        post.score(),
                        post.source(),
                        post.title() != null ? post.title() : "",
                        truncate(post.body(), 400),
                        post.permalink() != null ? post.permalink() : ""))
                .collect(Collectors.joining("\n"));
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "…";
    }
}
