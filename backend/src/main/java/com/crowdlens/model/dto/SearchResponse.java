package com.crowdlens.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Structured crowd opinion analysis for a product query")
public record SearchResponse(
        UUID id,
        String query,

        @Schema(description = "Detected product category, e.g. Grooming, Audio, Footwear")
        String productCategory,

        @Schema(description = "Detected product sub-category, e.g. Electric Trimmer")
        String productSubCategory,

        @Schema(description = "Overall score out of 100 based on blended sentiment, repetition, and confidence")
        int overallScore,

        @Schema(description = "Single crafted sentence verdict, e.g. \"Good buy if you want strong battery, but not ideal for thick beards.\"")
        String verdictSentence,

        @Schema(description = "Exactly 4 dynamic metrics relevant to this product, derived from discussion themes")
        List<Metric> metrics,

        @Schema(description = "Most repeated positive themes from community discussion")
        List<String> positives,

        @Schema(description = "Most repeated complaints from community discussion")
        List<String> complaints,

        @Schema(description = "User personas this product is best suited for")
        List<String> bestFor,

        @Schema(description = "User personas who should avoid this product")
        List<String> avoid,

        @Schema(description = "Paraphrased evidence snippets from actual community posts")
        List<EvidenceSnippet> evidenceSnippets,

        int postCount,
        List<String> sourcePlatforms,
        Instant analyzedAt,
        boolean cached,

        @Schema(description = "Resolved product image URL (from Reddit or Amazon). May be null if no image was found.")
        String productImageUrl,

        @Schema(description = "Base64-encoded product image (data URI prefix included). Used for share card rendering. May be null.")
        String productImageBase64,

        @Schema(description = "Competitor products in the same category, resolved from SQLite or AI-seeded.")
        List<CompetitorDto> competitors
) {

    @Builder
    @Schema(description = "A single dynamic metric for this product derived from community discussion themes")
    public record Metric(
            @Schema(description = "Clean label for the metric, e.g. Battery Life, Skin Comfort, Sound Quality")
            String label,

            @Schema(description = "Score out of 10 for this metric based on sentiment")
            double score,

            @Schema(description = "Short explanation of the score based on what users said")
            String explanation
    ) {}

    @Builder
    @Schema(description = "A paraphrased evidence snippet from a community post")
    public record EvidenceSnippet(
            @Schema(description = "Paraphrased summary of what the user said")
            String text,

            @Schema(description = "Subreddit or community source, e.g. r/malegrooming")
            String source,

            @Schema(description = "Direct link to the original post or comment")
            String permalink
    ) {}
}
