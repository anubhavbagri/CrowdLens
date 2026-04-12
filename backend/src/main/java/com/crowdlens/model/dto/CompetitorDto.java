package com.crowdlens.model.dto;

/**
 * Competitor entry returned by GET /api/competitors.
 *
 * @param name            Display name of the competitor product
 * @param score           Community score (0–100)
 * @param real            true = sourced from a real Reddit analysis;
 *                        false = AI-estimated placeholder (awaiting real search)
 * @param verdictSentence One-line verdict; null for AI-seeded entries
 * @param sourcePlatforms Comma-separated platforms (e.g. "reddit"); null for AI-seeded entries
 * @param postCount       Number of posts analysed; null for AI-seeded entries
 */
public record CompetitorDto(
        String name,
        Integer score,
        boolean real,
        String verdictSentence,
        String sourcePlatforms,
        Integer postCount
) {}
