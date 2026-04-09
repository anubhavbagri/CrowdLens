package com.crowdlens.provider.reddit;

import com.crowdlens.config.RedditProperties;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reddit OAuth2 API client (script-type authentication).
 * Primary data source — falls back to RedditJsonScraper on failure.
 */
@Slf4j
@Component
public class RedditApiClient {

    private final WebClient redditWebClient;
    private final WebClient authWebClient;
    private final RedditProperties props;
    private final RedditRateLimiter rateLimiter;

    private final AtomicReference<String> accessToken = new AtomicReference<>();
    private final AtomicReference<Instant> tokenExpiry = new AtomicReference<>(Instant.EPOCH);

    public RedditApiClient(@Qualifier("redditWebClient") WebClient redditWebClient,
            WebClient.Builder webClientBuilder,
            RedditProperties props,
            RedditRateLimiter rateLimiter) {
        this.redditWebClient = redditWebClient;
        this.authWebClient = webClientBuilder
                .baseUrl("https://www.reddit.com")
                .defaultHeader("User-Agent", props.userAgent())
                .build();
        this.props = props;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Eagerly refresh the OAuth token at startup so the first user request
     * doesn't pay the ~10s authentication cost.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpToken() {
        try {
            log.info("Warming up Reddit OAuth2 token at startup...");
            ensureAuthenticated();
        } catch (Exception e) {
            log.warn("Reddit OAuth2 token warmup failed (will retry on first request): {}", e.getMessage());
        }
    }

    /**
     * Search Reddit via OAuth2 API.
     *
     * @param query Search string
     * @param limit Max results (Reddit max 100)
     * @return Raw JSON search results from Reddit
     */
    @CircuitBreaker(name = "redditApi", fallbackMethod = "searchFallback")
    public List<JsonNode> searchPosts(String query, int limit) {
        rateLimiter.acquireApiToken();
        ensureAuthenticated();

        final int maxLimit = Math.min(limit, 100); // Reddit API max

        log.debug("Reddit API search: query='{}', limit={}", query, maxLimit);

        JsonNode response = redditWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search.json")
                        .queryParam("q", query)
                        .queryParam("sort", "relevance")
                        .queryParam("t", "year")
                        .queryParam("limit", maxLimit)
                        .queryParam("type", "link")
                        .build())
                .header("Authorization", "Bearer " + accessToken.get())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("data") || !response.get("data").has("children")) {
            log.warn("Reddit API returned empty/malformed response");
            return Collections.emptyList();
        }

        List<JsonNode> posts = new ArrayList<>();
        for (JsonNode child : response.get("data").get("children")) {
            if (child.has("data")) {
                posts.add(child.get("data"));
            }
        }

        log.info("Reddit API returned {} posts for query '{}'", posts.size(), query);
        return posts;
    }

    /**
     * Fetch comments for a given post.
     */
    @CircuitBreaker(name = "redditApi")
    public List<JsonNode> fetchComments(String postId, int limit) {
        rateLimiter.acquireApiToken();
        ensureAuthenticated();

        log.debug("Fetching comments for post: {}", postId);

        JsonNode response = redditWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/comments/{postId}.json")
                        .queryParam("limit", Math.min(limit, 50))
                        .queryParam("sort", "top")
                        .queryParam("depth", 1)
                        .build(postId))
                .header("Authorization", "Bearer " + accessToken.get())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.isArray() || response.size() < 2) {
            return Collections.emptyList();
        }

        // Reddit returns [post, comments_listing]
        JsonNode commentsListing = response.get(1);
        List<JsonNode> comments = new ArrayList<>();

        if (commentsListing.has("data") && commentsListing.get("data").has("children")) {
            for (JsonNode child : commentsListing.get("data").get("children")) {
                if (child.has("data") && "t1".equals(child.path("kind").asText())) {
                    comments.add(child.get("data"));
                }
            }
        }

        log.debug("Fetched {} comments for post {}", comments.size(), postId);
        return comments;
    }

    /**
     * Circuit breaker fallback — returns empty list.
     */
    @SuppressWarnings("unused")
    private List<JsonNode> searchFallback(String query, int limit, Throwable t) {
        log.warn("Reddit API circuit breaker open for query '{}': {}", query, t.getMessage());
        return Collections.emptyList();
    }

    /**
     * Ensures we have a valid OAuth2 access token.
     */
    private synchronized void ensureAuthenticated() {
        if (accessToken.get() != null && Instant.now().isBefore(tokenExpiry.get())) {
            return; // Token still valid
        }

        log.info("Refreshing Reddit OAuth2 token...");

        String credentials = Base64.getEncoder()
                .encodeToString((props.clientId() + ":" + props.clientSecret()).getBytes());

        JsonNode tokenResponse = authWebClient.post()
                .uri("/api/v1/access_token")
                .header("Authorization", "Basic " + credentials)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters
                        .fromFormData("grant_type", "password")
                        .with("username", props.username())
                        .with("password", props.password()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (tokenResponse != null && tokenResponse.has("access_token")) {
            accessToken.set(tokenResponse.get("access_token").asText());
            int expiresIn = tokenResponse.path("expires_in").asInt(3600);
            tokenExpiry.set(Instant.now().plusSeconds(expiresIn - 60)); // Refresh 60s early
            log.info("Reddit OAuth2 token refreshed, expires in {}s", expiresIn);
        } else {
            throw new RuntimeException("Failed to obtain Reddit OAuth2 token: " + tokenResponse);
        }
    }

    /**
     * Simple health check — tries to fetch /api/v1/me.
     */
    public boolean healthCheck() {
        try {
            ensureAuthenticated();
            JsonNode me = redditWebClient.get()
                    .uri("/api/v1/me")
                    .header("Authorization", "Bearer " + accessToken.get())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return me != null && me.has("name");
        } catch (Exception e) {
            log.warn("Reddit API health check failed: {}", e.getMessage());
            return false;
        }
    }
}
