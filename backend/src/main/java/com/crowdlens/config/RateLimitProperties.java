package com.crowdlens.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        BucketConfig redditApi,
        BucketConfig redditScraper) {
    public record BucketConfig(
            int capacity,
            int refillTokens,
            int refillDurationSeconds) {
    }
}
