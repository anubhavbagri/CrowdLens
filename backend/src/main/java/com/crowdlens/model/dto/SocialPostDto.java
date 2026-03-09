package com.crowdlens.model.dto;

import lombok.Builder;

import java.time.Instant;

/**
 * Platform-agnostic representation of a social media post.
 * All platform providers normalize their data into this format.
 */
@Builder
public record SocialPostDto(
        String platformId,
        String platform,
        String source, // subreddit, hashtag, etc.
        String title,
        String body,
        int score,
        String permalink,
        Instant postedAt) {
}
