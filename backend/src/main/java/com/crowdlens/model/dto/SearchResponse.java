package com.crowdlens.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResponse(
        UUID id,
        String query,
        int overallScore,
        String overallVerdict,
        String verdictSummary,
        List<CategoryAnalysis> categories,
        List<Testimonial> testimonials,
        PersonaAnalysis personaAnalysis,
        int postCount,
        List<String> sourcePlatforms,
        Instant analyzedAt,
        boolean cached) {

    @Builder
    public record CategoryAnalysis(
            String name,
            String rating,
            String summary,
            List<String> highlights) {
    }

    @Builder
    public record Testimonial(
            String text,
            String sentiment,
            String source,
            String platform,
            String permalink) {
    }

    @Builder
    public record PersonaAnalysis(
            String question,
            List<PersonaFit> fits) {
    }

    @Builder
    public record PersonaFit(
            String persona,
            String verdict,
            String reason) {
    }
}
