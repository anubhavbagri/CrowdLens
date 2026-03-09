package com.crowdlens.provider.reddit;

import com.crowdlens.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Token bucket rate limiter for Reddit requests.
 * Provides separate buckets for OAuth API and JSON scraper endpoints.
 */
@Slf4j
@Component
public class RedditRateLimiter {

    private final Bucket apiBucket;
    private final Bucket scraperBucket;

    public RedditRateLimiter(RateLimitProperties props) {
        this.apiBucket = createBucket(props.redditApi());
        this.scraperBucket = createBucket(props.redditScraper());
        log.info("Reddit rate limiter initialized — API: {}/{}s, Scraper: {}/{}s",
                props.redditApi().capacity(), props.redditApi().refillDurationSeconds(),
                props.redditScraper().capacity(), props.redditScraper().refillDurationSeconds());
    }

    /**
     * Acquire a token from the API bucket.
     * Blocks until a token is available.
     */
    public void acquireApiToken() {
        try {
            apiBucket.asBlocking().consume(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for API rate limit token", e);
        }
    }

    /**
     * Acquire a token from the scraper bucket.
     * Blocks until a token is available.
     */
    public void acquireScraperToken() {
        try {
            scraperBucket.asBlocking().consume(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for scraper rate limit token", e);
        }
    }

    private Bucket createBucket(RateLimitProperties.BucketConfig config) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(config.capacity())
                .refillGreedy(config.refillTokens(), Duration.ofSeconds(config.refillDurationSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
