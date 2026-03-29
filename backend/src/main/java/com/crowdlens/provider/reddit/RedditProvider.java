package com.crowdlens.provider.reddit;

import com.crowdlens.model.dto.SocialPostDto;
import com.crowdlens.provider.PlatformProvider;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Reddit platform provider — implements the PlatformProvider strategy interface.
 *
 * Uses a Chain of Responsibility pattern for data acquisition:
 * 1. Try RedditApiClient (OAuth2, higher quality)
 * 2. Fall back to RedditJsonScraper (no auth, stealth)
 * 3. Aggregate and deduplicate results via RedditDataAggregator (in-memory, per-request)
 * 4. Fetch top comments from best posts for richer opinions
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedditProvider implements PlatformProvider {

    private static final int TOP_POSTS_FOR_COMMENTS = 10;
    private static final int COMMENTS_PER_POST = 10;

    private final RedditApiClient apiClient;
    private final RedditJsonScraper jsonScraper;
    private final RedditDataAggregator aggregator;

    @Override
    public String getPlatformName() {
        return "reddit";
    }

    @Override
    public List<SocialPostDto> search(String query, int limit, int maxComments) {
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("RedditProvider.search: query='{}', limit={}", query, limit);
        log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // 1. Try OAuth2 API first
        List<JsonNode> apiPosts = Collections.emptyList();
        try {
            apiPosts = apiClient.searchPosts(query, limit);
            logRedditResponse("OAuth2 API", apiPosts);
        } catch (Exception e) {
            log.warn("Reddit API failed, falling back to JSON scraper: {}", e.getMessage());
        }

        // 2. If API returned nothing, fall back to JSON scraper
        List<JsonNode> scraperPosts = Collections.emptyList();
        if (apiPosts.isEmpty()) {
            try {
                scraperPosts = jsonScraper.searchPosts(query, limit);
                logRedditResponse("JSON Scraper", scraperPosts);
            } catch (Exception e) {
                log.error("Reddit JSON scraper also failed: {}", e.getMessage());
            }
        }

        // 3. Aggregate & deduplicate (by ID, in-memory, API preferred over scraper)
        List<SocialPostDto> posts = aggregator.aggregate(apiPosts, scraperPosts);

        // 4. Rank by score and take only top N (user limit)
        if (posts.size() > limit) {
            posts = posts.stream()
                    .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                    .limit(limit)
                    .toList();
            log.info("Ranked and capped posts to top {} (by score)", limit);
        }

        // Raw posts for comment fetching (combine both sources, dedup by ID)
        List<JsonNode> allRawPosts = deduplicateRaw(apiPosts, scraperPosts);

        // 5. Fetch top comments from best posts for richer opinions
        List<SocialPostDto> commentPosts = fetchCommentsFromTopPosts(allRawPosts, query, maxComments);
        if (!commentPosts.isEmpty()) {
            log.info("Adding {} comment-based opinions to results", commentPosts.size());
            List<SocialPostDto> combined = new ArrayList<>(posts);
            combined.addAll(commentPosts);
            posts = combined;
        }

        if (posts.isEmpty()) {
            log.warn("⚠️ No posts found for query '{}'", query);
        } else {
            log.info("✅ RedditProvider returning {} total items (posts + comments) for query '{}'",
                    posts.size(), query);
        }

        return posts;
    }

    /**
     * Deduplicates raw JsonNode posts from two sources by ID (API preferred).
     */
    private List<JsonNode> deduplicateRaw(List<JsonNode> apiPosts, List<JsonNode> scraperPosts) {
        Map<String, JsonNode> unique = new LinkedHashMap<>();
        for (JsonNode post : apiPosts) {
            String id = post.path("id").asText("");
            if (!id.isEmpty()) unique.putIfAbsent(id, post);
        }
        for (JsonNode post : scraperPosts) {
            String id = post.path("id").asText("");
            if (!id.isEmpty()) unique.putIfAbsent(id, post);
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * Fetches comments from the highest-scored posts in parallel.
     * Each post's comments are fetched concurrently via CompletableFuture,
     * reducing total time from O(n*latency) to O(max_single_latency).
     */
    private List<SocialPostDto> fetchCommentsFromTopPosts(List<JsonNode> rawPosts, String query, int maxComments) {
        if (rawPosts.isEmpty()) return Collections.emptyList();

        // Sort by score and take top N posts
        List<JsonNode> topPosts = rawPosts.stream()
                .sorted((a, b) -> Integer.compare(b.path("score").asInt(0), a.path("score").asInt(0)))
                .limit(TOP_POSTS_FOR_COMMENTS)
                .toList();

        log.info("Fetching comments from top {} posts in parallel...", topPosts.size());

        // Fan-out: fetch all posts' comments concurrently
        record PostComments(JsonNode post, List<JsonNode> comments) {}

        List<CompletableFuture<PostComments>> futures = topPosts.stream()
                .filter(post -> !post.path("id").asText("").isEmpty())
                .map(post -> CompletableFuture.supplyAsync(() -> {
                    String postId = post.path("id").asText();
                    try {
                        List<JsonNode> comments = apiClient.fetchComments(postId, COMMENTS_PER_POST);
                        log.info("  → Post '{}' — {} comments fetched",
                                truncate(post.path("title").asText("untitled"), 60), comments.size());
                        return new PostComments(post, comments);
                    } catch (Exception e) {
                        log.debug("Failed to fetch comments for post {}: {}", postId, e.getMessage());
                        return new PostComments(post, Collections.emptyList());
                    }
                }))
                .toList();

        // Wait for all fetches to complete
        List<PostComments> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // Collect and filter comments
        List<SocialPostDto> commentPosts = new ArrayList<>();
        Set<String> seenCommentIds = new HashSet<>();

        for (PostComments pc : results) {
            if (commentPosts.size() >= maxComments) break;

            String postTitle = pc.post().path("title").asText("untitled");
            String subreddit = pc.post().path("subreddit_name_prefixed").asText(
                    "r/" + pc.post().path("subreddit").asText("unknown"));
            String postPermalink = pc.post().path("permalink").asText("");

            for (JsonNode comment : pc.comments()) {
                if (commentPosts.size() >= maxComments) break;

                String commentId = comment.path("id").asText("");
                if (commentId.isEmpty() || seenCommentIds.contains(commentId)) continue;

                String body = comment.path("body").asText("");
                String author = comment.path("author").asText("");
                int score = comment.path("score").asInt(0);

                if (body.length() < 30 || "[deleted]".equals(body) || "[removed]".equals(body)) continue;
                if ("[deleted]".equals(author) || "AutoModerator".equals(author)) continue;

                double createdUtc = comment.path("created_utc").asDouble(0);
                Instant postedAt = createdUtc > 0 ? Instant.ofEpochSecond((long) createdUtc) : null;

                String commentPermalink = comment.path("permalink").asText(postPermalink);
                if (!commentPermalink.startsWith("http")) {
                    commentPermalink = "https://reddit.com" + commentPermalink;
                }

                commentPosts.add(SocialPostDto.builder()
                        .platformId("reddit_comment_" + commentId)
                        .platform("reddit")
                        .source(subreddit)
                        .title("Re: " + postTitle)
                        .body(body)
                        .score(score)
                        .permalink(commentPermalink)
                        .postedAt(postedAt)
                        .build());

                seenCommentIds.add(commentId);
            }
        }

        log.info("Collected {} quality comments from {} posts (parallel fetch)", commentPosts.size(), topPosts.size());
        return commentPosts;
    }

    /**
     * Logs detailed info about Reddit search results for debugging.
     */
    private void logRedditResponse(String source, List<JsonNode> posts) {
        if (posts.isEmpty()) {
            log.info("[{}] Returned 0 posts", source);
            return;
        }

        log.info("[{}] Returned {} posts:", source, posts.size());
        for (int i = 0; i < Math.min(posts.size(), 15); i++) {
            JsonNode post = posts.get(i);
            String title = post.path("title").asText("(no title)");
            String subreddit = post.path("subreddit_name_prefixed").asText("r/?");
            int score = post.path("score").asInt(0);
            int numComments = post.path("num_comments").asInt(0);
            String selftext = post.path("selftext").asText("");

            log.info("  [{}] {} | score:{} | comments:{} | '{}' | body_length:{}",
                    i + 1, subreddit, score, numComments, truncate(title, 70), selftext.length());
        }
        if (posts.size() > 15) {
            log.info("  ... and {} more posts", posts.size() - 15);
        }
    }

    @Override
    public boolean healthCheck() {
        return apiClient.healthCheck();
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
