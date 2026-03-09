package com.crowdlens.provider.reddit;

import com.crowdlens.model.dto.SocialPostDto;
import com.crowdlens.provider.PlatformProvider;
import com.crowdlens.service.ScrapeCursorService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reddit platform provider — implements the PlatformProvider strategy
 * interface.
 *
 * Uses a Chain of Responsibility pattern for data acquisition:
 * 1. Try RedditApiClient (OAuth2, higher quality)
 * 2. Fall back to RedditJsonScraper (no auth, stealth)
 * 3. Apply incremental cursor to skip already-seen posts
 * 4. Fetch top comments from best posts for richer opinions
 * 5. Aggregate and normalize results via RedditDataAggregator
 * 6. Update cursor with newly processed post IDs
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
    private final ScrapeCursorService cursorService;

    @Override
    public String getPlatformName() {
        return "reddit";
    }

    @Override
    public List<SocialPostDto> search(String query, int limit, int maxComments) {
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

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

        // 2. If API returned nothing or failed, fall back to JSON scraper
        List<JsonNode> scraperPosts = Collections.emptyList();
        if (apiPosts.isEmpty()) {
            try {
                scraperPosts = jsonScraper.searchPosts(query, limit);
                logRedditResponse("JSON Scraper", scraperPosts);
            } catch (Exception e) {
                log.error("Reddit JSON scraper also failed: {}", e.getMessage());
            }
        }

        // 3. Combine raw posts from both sources
        List<JsonNode> allRawPosts = new ArrayList<>();
        allRawPosts.addAll(apiPosts);
        allRawPosts.addAll(scraperPosts);

        // 4. Apply incremental cursor — filter out already-seen posts
        List<JsonNode> newPosts = applyIncrementalCursor(normalizedQuery, allRawPosts);

        // 5. Aggregate & normalize the new posts
        List<SocialPostDto> posts = aggregator.normalize(newPosts);

        // 5b. Rank by score and take only top N posts (user limit)
        if (posts.size() > limit) {
            posts = posts.stream()
                    .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                    .limit(limit)
                    .toList();
            log.info("Ranked and capped posts to top {} (by score)", limit);
        }

        // 6. Fetch top comments from best posts for richer opinions (capped at
        // maxComments)
        List<SocialPostDto> commentPosts = fetchCommentsFromTopPosts(newPosts, query, maxComments);
        if (!commentPosts.isEmpty()) {
            log.info("Adding {} comment-based opinions to results", commentPosts.size());
            List<SocialPostDto> combined = new ArrayList<>(posts);
            combined.addAll(commentPosts);
            posts = combined;
        }

        // 7. Update cursor with newly processed post IDs
        updateCursorAfterSearch(normalizedQuery, newPosts);

        if (posts.isEmpty()) {
            log.warn("⚠️ No NEW posts found for query '{}' (cursor may have filtered all)", query);
        } else {
            log.info("✅ RedditProvider returning {} total items (posts + comments) for query '{}'",
                    posts.size(), query);
        }

        return posts;
    }

    /**
     * Applies incremental cursor to filter out already-seen posts.
     * Returns only posts that are NEW (not in the cursor's recent IDs window).
     */
    private List<JsonNode> applyIncrementalCursor(String normalizedQuery, List<JsonNode> rawPosts) {
        if (rawPosts.isEmpty())
            return rawPosts;

        // Extract IDs and dates from raw posts
        List<String> postIds = new ArrayList<>();
        List<Instant> postDates = new ArrayList<>();

        for (JsonNode post : rawPosts) {
            String id = post.path("id").asText("");
            double createdUtc = post.path("created_utc").asDouble(0);
            Instant postedAt = createdUtc > 0 ? Instant.ofEpochSecond((long) createdUtc) : null;

            postIds.add(id);
            postDates.add(postedAt);
        }

        // Filter via cursor service
        Set<String> newPostIds = cursorService.filterNewPostIds("reddit", normalizedQuery, postIds, postDates);

        // Keep only new posts
        List<JsonNode> filteredPosts = new ArrayList<>();
        for (JsonNode post : rawPosts) {
            String id = post.path("id").asText("");
            if (newPostIds.contains(id)) {
                filteredPosts.add(post);
            }
        }

        int skipped = rawPosts.size() - filteredPosts.size();
        if (skipped > 0) {
            log.info("🔄 Incremental cursor: skipped {} already-seen posts, {} new posts remain",
                    skipped, filteredPosts.size());
        }

        return filteredPosts;
    }

    /**
     * Updates the cursor with all post IDs that were just processed.
     */
    private void updateCursorAfterSearch(String normalizedQuery, List<JsonNode> processedPosts) {
        if (processedPosts.isEmpty())
            return;

        List<String> processedIds = processedPosts.stream()
                .map(post -> post.path("id").asText(""))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toList());

        // Find the newest post date
        Instant newestDate = processedPosts.stream()
                .map(post -> {
                    double createdUtc = post.path("created_utc").asDouble(0);
                    return createdUtc > 0 ? Instant.ofEpochSecond((long) createdUtc) : null;
                })
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        cursorService.updateCursor("reddit", normalizedQuery, processedIds, newestDate);
    }

    /**
     * Fetches top comments from the highest-scored posts.
     * Comments often contain the most valuable, detailed opinions.
     * Uses a separate incremental cursor ("reddit_comments") to skip already-seen
     * comments.
     */
    private List<SocialPostDto> fetchCommentsFromTopPosts(List<JsonNode> rawPosts, String query, int maxComments) {
        if (rawPosts.isEmpty())
            return Collections.emptyList();

        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        // Sort by score and take top N posts
        List<JsonNode> topPosts = rawPosts.stream()
                .sorted((a, b) -> Integer.compare(b.path("score").asInt(0), a.path("score").asInt(0)))
                .limit(TOP_POSTS_FOR_COMMENTS)
                .toList();

        log.info("Fetching comments from top {} posts (by score)...", topPosts.size());

        // Collect all raw comments first
        List<JsonNode> allComments = new ArrayList<>();
        Map<JsonNode, String[]> commentContext = new LinkedHashMap<>(); // comment → [postTitle, subreddit, permalink]

        for (JsonNode post : topPosts) {
            String postId = post.path("id").asText();
            String postTitle = post.path("title").asText("untitled");
            String subreddit = post.path("subreddit_name_prefixed").asText(
                    "r/" + post.path("subreddit").asText("unknown"));
            String permalink = post.path("permalink").asText("");

            if (postId.isEmpty())
                continue;

            try {
                List<JsonNode> comments = apiClient.fetchComments(postId, COMMENTS_PER_POST);
                log.info("  → Post '{}' ({}) — {} comments fetched",
                        truncate(postTitle, 60), subreddit, comments.size());

                for (JsonNode comment : comments) {
                    allComments.add(comment);
                    commentContext.put(comment, new String[] { postTitle, subreddit, permalink });
                }
            } catch (Exception e) {
                log.debug("Failed to fetch comments for post {}: {}", postId, e.getMessage());
            }
        }

        if (allComments.isEmpty())
            return Collections.emptyList();

        // Apply incremental cursor to filter already-seen comments
        List<String> commentIds = allComments.stream()
                .map(c -> c.path("id").asText(""))
                .collect(Collectors.toList());
        List<Instant> commentDates = allComments.stream()
                .map(c -> {
                    double ts = c.path("created_utc").asDouble(0);
                    return ts > 0 ? Instant.ofEpochSecond((long) ts) : null;
                })
                .collect(Collectors.toList());

        Set<String> newCommentIds = cursorService.filterNewPostIds(
                "reddit_comments", normalizedQuery, commentIds, commentDates);

        int skipped = commentIds.size() - newCommentIds.size();
        if (skipped > 0) {
            log.info("🔄 Comment cursor: skipped {} already-seen comments, {} new remain",
                    skipped, newCommentIds.size());
        }

        // Process only new comments
        List<SocialPostDto> commentPosts = new ArrayList<>();
        List<String> processedCommentIds = new ArrayList<>();
        Instant newestCommentDate = null;

        for (JsonNode comment : allComments) {
            // Cap total comments to save AI tokens
            if (commentPosts.size() >= maxComments)
                break;

            String commentId = comment.path("id").asText("");
            if (!newCommentIds.contains(commentId))
                continue;

            String body = comment.path("body").asText("");
            String author = comment.path("author").asText("");
            int score = comment.path("score").asInt(0);

            // Skip low-quality comments
            if (body.length() < 30 || "[deleted]".equals(body) || "[removed]".equals(body))
                continue;
            if ("[deleted]".equals(author) || "AutoModerator".equals(author))
                continue;

            String[] ctx = commentContext.get(comment);
            String postTitle = ctx[0];
            String subreddit = ctx[1];
            String fallbackPermalink = ctx[2];

            double createdUtc = comment.path("created_utc").asDouble(0);
            Instant postedAt = createdUtc > 0 ? Instant.ofEpochSecond((long) createdUtc) : null;

            String commentPermalink = comment.path("permalink").asText(fallbackPermalink);
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

            processedCommentIds.add(commentId);
            if (postedAt != null && (newestCommentDate == null || postedAt.isAfter(newestCommentDate))) {
                newestCommentDate = postedAt;
            }

            log.debug("    Comment by u/{} (score: {}): {}",
                    author, score, truncate(body, 100));
        }

        // Update comment cursor
        if (!processedCommentIds.isEmpty()) {
            cursorService.updateCursor("reddit_comments", normalizedQuery,
                    processedCommentIds, newestCommentDate);
        }

        log.info("Collected {} NEW quality comments from {} posts", commentPosts.size(), topPosts.size());
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
                    i + 1, subreddit, score, numComments, truncate(title, 70),
                    selftext.length());
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
        if (text == null)
            return "";
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
