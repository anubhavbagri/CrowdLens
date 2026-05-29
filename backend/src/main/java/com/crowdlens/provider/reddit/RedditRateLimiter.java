package com.crowdlens.provider.reddit;

import com.crowdlens.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * WHAT IT DOES:
 * Restricts outgoing crawler traffic to Reddit's network using separate rate limit buckets
 * for the official OAuth API and the unauthenticated JSON scraper.
 *
 * WHY IT'S NEEDED:
 * Prevents the application from getting banned (HTTP 429 Too Many Requests) by Reddit's CDN and WAF,
 * ensuring outbound requests follow Reddit's API usage policies.
 *
 * HOW IT WORKS:
 * - Token Bucket Algorithm: Employs Bucket4j to define rate limits. Outbound crawl tasks consume tokens. If no tokens are available, the calling thread blocks until the token regenerates.
 * - Greedy Refill strategy: Refills tokens steadily over the interval (e.g. adding 1 token every second instead of 60 tokens in a batch at minute marks). This smooths request bursts, mimicking human browsing behavior.
 * - Dual Isolation: Isolates the official API bucket from the scraper bucket to prevent scraper delays from affecting API workflows.
 *
 * SPRING ANNOTATIONS EXPLAINED:
 * - @Component: Marks this class as a Spring Bean managed under context registry.
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
