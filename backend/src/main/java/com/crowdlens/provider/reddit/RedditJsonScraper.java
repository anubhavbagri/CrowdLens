package com.crowdlens.provider.reddit;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fallback Reddit data source using old.reddit.com JSON endpoints (no auth
 * required).
 * Used when OAuth2 API is rate-limited or unavailable.
 *
 * Stealth measures:
 * - User-Agent rotation
 * - Random jitter between requests (500-2000ms)
 */
@Slf4j
@Component
public class RedditJsonScraper {

    private final WebClient publicWebClient;
    private final RedditRateLimiter rateLimiter;

    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:123.0) Gecko/20100101 Firefox/123.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.3 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0",
            "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:122.0) Gecko/20100101 Firefox/122.0",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:123.0) Gecko/20100101 Firefox/123.0",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36 OPR/107.0.0.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36");

    public RedditJsonScraper(@Qualifier("redditPublicWebClient") WebClient publicWebClient,
            RedditRateLimiter rateLimiter) {
        this.publicWebClient = publicWebClient;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Search Reddit via old.reddit.com JSON endpoint (no auth).
     */
    public List<JsonNode> searchPosts(String query, int limit) {
        rateLimiter.acquireScraperToken();
        addJitter();

        final int maxLimit = Math.min(limit, 100);

        log.debug("Reddit JSON scraper search: query='{}', limit={}", query, maxLimit);

        try {
            JsonNode response = publicWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search.json")
                            .queryParam("q", query)
                            .queryParam("sort", "relevance")
                            .queryParam("t", "year")
                            .queryParam("limit", maxLimit)
                            .queryParam("type", "link")
                            .build())
                    .header("User-Agent", getRandomUserAgent())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("data") || !response.get("data").has("children")) {
                log.warn("Reddit JSON scraper returned empty/malformed response");
                return Collections.emptyList();
            }

            List<JsonNode> posts = new ArrayList<>();
            for (JsonNode child : response.get("data").get("children")) {
                if (child.has("data")) {
                    posts.add(child.get("data"));
                }
            }

            log.info("Reddit JSON scraper returned {} posts for query '{}'", posts.size(), query);
            return posts;

        } catch (Exception e) {
            log.error("Reddit JSON scraper failed for query '{}': {}", query, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getRandomUserAgent() {
        return USER_AGENTS.get(ThreadLocalRandom.current().nextInt(USER_AGENTS.size()));
    }

    /**
     * Random jitter between 500-2000ms to avoid detection.
     */
    private void addJitter() {
        try {
            int jitter = ThreadLocalRandom.current().nextInt(500, 2001);
            Thread.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
