package com.crowdlens.provider.reddit;

import com.crowdlens.model.dto.SocialPostDto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates, deduplicates, and filters Reddit data from API and scraper
 * sources.
 * Normalizes raw Reddit JSON into platform-agnostic SocialPostDto objects.
 */
@Slf4j
@Component
public class RedditDataAggregator {

    private static final int MIN_CONTENT_LENGTH = 20;
    private static final Set<String> BOT_ACCOUNTS = Set.of(
            "AutoModerator", "RemindMeBot", "WikiTextBot", "TotesMessenger",
            "sneakpeekbot", "RepostSleuthBot", "CommonMisspellingBot",
            "HelperBot_", "B0tRank", "LinkifyBot");

    /**
     * Aggregates posts from multiple sources, deduplicates, filters, and
     * normalizes.
     */
    public List<SocialPostDto> aggregate(List<JsonNode> apiPosts, List<JsonNode> scraperPosts) {
        Map<String, JsonNode> uniquePosts = new LinkedHashMap<>();

        // Prefer API posts over scraper posts (higher quality)
        for (JsonNode post : apiPosts) {
            String id = post.path("id").asText();
            if (!id.isEmpty()) {
                uniquePosts.putIfAbsent(id, post);
            }
        }
        for (JsonNode post : scraperPosts) {
            String id = post.path("id").asText();
            if (!id.isEmpty()) {
                uniquePosts.putIfAbsent(id, post);
            }
        }

        log.debug("Deduplication: {} API + {} scraper → {} unique",
                apiPosts.size(), scraperPosts.size(), uniquePosts.size());

        List<SocialPostDto> result = uniquePosts.values().stream()
                .filter(this::passesFilters)
                .map(this::toSocialPostDto)
                .sorted(Comparator.comparingInt(SocialPostDto::score).reversed())
                .collect(Collectors.toList());

        log.info("Aggregation complete: {} posts after filtering", result.size());
        return result;
    }

    /**
     * Normalizes a single list of posts (from one source).
     */
    public List<SocialPostDto> normalize(List<JsonNode> posts) {
        return aggregate(posts, Collections.emptyList());
    }

    private boolean passesFilters(JsonNode post) {
        // Filter deleted/removed posts
        String selftext = post.path("selftext").asText("");
        String title = post.path("title").asText("");
        if ("[deleted]".equals(selftext) || "[removed]".equals(selftext))
            return false;
        if ("[deleted]".equals(title) || "[removed]".equals(title))
            return false;

        // Filter bot accounts
        String author = post.path("author").asText("");
        if (BOT_ACCOUNTS.contains(author) || author.equals("[deleted]"))
            return false;

        // Minimum content length
        String content = title + " " + selftext;
        if (content.trim().length() < MIN_CONTENT_LENGTH)
            return false;

        return true;
    }

    private SocialPostDto toSocialPostDto(JsonNode post) {
        String selftext = post.path("selftext").asText("");
        String title = post.path("title").asText("");
        String subreddit = post.path("subreddit_name_prefixed").asText(
                "r/" + post.path("subreddit").asText("unknown"));
        String permalink = post.path("permalink").asText("");

        // Reddit timestamps are in epoch seconds (double)
        double createdUtc = post.path("created_utc").asDouble(0);
        Instant postedAt = createdUtc > 0 ? Instant.ofEpochSecond((long) createdUtc) : null;

        return SocialPostDto.builder()
                .platformId("reddit_" + post.path("id").asText())
                .platform("reddit")
                .source(subreddit)
                .title(title)
                .body(selftext)
                .score(post.path("score").asInt(0))
                .permalink(permalink.startsWith("http") ? permalink : "https://reddit.com" + permalink)
                .postedAt(postedAt)
                .build();
    }
}
