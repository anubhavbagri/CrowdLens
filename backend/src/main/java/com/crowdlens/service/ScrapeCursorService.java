package com.crowdlens.service;

import com.crowdlens.model.entity.ScrapeCursor;
import com.crowdlens.repository.ScrapeCursorRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Manages incremental scrape cursors per platform/query.
 *
 * Algorithm (from architecture.md):
 * 1. Fetch latest batch of posts
 * 2. For each post: is post.date <= cursor.lastItemDate?
 * - YES → already seen territory → skip
 * - NO → process post, update cursor
 * 3. Result: repeated runs skip already-seen posts, saving bandwidth and
 * reducing ban risk.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScrapeCursorService {

    private static final int MAX_RECENT_IDS = 100; // Bloom filter-style dedup window

    private final ScrapeCursorRepository cursorRepo;
    private final ObjectMapper objectMapper;

    /**
     * Retrieves the cursor for a platform/query combination, if one exists.
     */
    public Optional<ScrapeCursor> getCursor(String platform, String queryNormalized) {
        return cursorRepo.findByPlatformAndQueryNormalized(platform, queryNormalized);
    }

    /**
     * Checks if a post ID has been recently seen (exists in the cursor's recentIds
     * set).
     */
    public boolean isRecentlySeen(ScrapeCursor cursor, String postId) {
        Set<String> recentIds = deserializeRecentIds(cursor.getRecentIds());
        return recentIds.contains(postId);
    }

    /**
     * Filters out posts that are already seen based on cursor state.
     * Returns only NEW posts (not in recentIds and posted after lastItemDate).
     *
     * @param platform        Platform name (e.g. "reddit")
     * @param queryNormalized Normalized search query
     * @param postIds         List of post IDs in order
     * @param postDates       Corresponding post dates (same order as postIds)
     * @return Set of post IDs that are NEW and should be processed
     */
    public Set<String> filterNewPostIds(String platform, String queryNormalized,
            List<String> postIds, List<Instant> postDates) {
        Optional<ScrapeCursor> cursorOpt = getCursor(platform, queryNormalized);

        if (cursorOpt.isEmpty()) {
            log.info("No cursor found for {}/{} — all {} posts are new",
                    platform, queryNormalized, postIds.size());
            return new LinkedHashSet<>(postIds); // All posts are new
        }

        ScrapeCursor cursor = cursorOpt.get();
        Set<String> recentIds = deserializeRecentIds(cursor.getRecentIds());
        Instant lastItemDate = cursor.getLastItemDate();

        Set<String> newPostIds = new LinkedHashSet<>();
        int skippedByDate = 0;
        int skippedById = 0;

        for (int i = 0; i < postIds.size(); i++) {
            String postId = postIds.get(i);
            Instant postDate = i < postDates.size() ? postDates.get(i) : null;

            // Skip if already in recent IDs (bloom filter-style)
            if (recentIds.contains(postId)) {
                skippedById++;
                continue;
            }

            // Skip if posted before or at the cursor's high-water mark
            if (lastItemDate != null && postDate != null && !postDate.isAfter(lastItemDate)) {
                skippedByDate++;
                continue;
            }

            newPostIds.add(postId);
        }

        log.info("Cursor filter for {}/{}: {} total → {} new, {} skipped (by date: {}, by ID: {})",
                platform, queryNormalized, postIds.size(), newPostIds.size(),
                skippedByDate + skippedById, skippedByDate, skippedById);

        return newPostIds;
    }

    /**
     * Updates (or creates) the cursor after a successful scrape.
     * Advances the high-water mark to the newest post date and
     * adds all processed post IDs to the recent set.
     *
     * @param platform        Platform name
     * @param queryNormalized Normalized search query
     * @param processedIds    IDs of posts that were just processed
     * @param newestPostDate  The newest post date from this batch
     */
    @Transactional
    public void updateCursor(String platform, String queryNormalized,
            List<String> processedIds, Instant newestPostDate) {
        if (processedIds.isEmpty()) {
            log.debug("No posts processed, skipping cursor update for {}/{}", platform, queryNormalized);
            return;
        }

        ScrapeCursor cursor = cursorRepo.findByPlatformAndQueryNormalized(platform, queryNormalized)
                .orElse(ScrapeCursor.builder()
                        .platform(platform)
                        .queryNormalized(queryNormalized)
                        .build());

        // Advance the high-water mark
        if (newestPostDate != null) {
            if (cursor.getLastItemDate() == null || newestPostDate.isAfter(cursor.getLastItemDate())) {
                cursor.setLastItemDate(newestPostDate);
            }
        }

        // Update recent IDs (keep last MAX_RECENT_IDS)
        Set<String> recentIds = deserializeRecentIds(cursor.getRecentIds());
        recentIds.addAll(processedIds);

        // Trim to max size (remove oldest entries)
        if (recentIds.size() > MAX_RECENT_IDS) {
            List<String> idList = new ArrayList<>(recentIds);
            recentIds = new LinkedHashSet<>(idList.subList(idList.size() - MAX_RECENT_IDS, idList.size()));
        }

        cursor.setRecentIds(serializeRecentIds(recentIds));
        cursorRepo.save(cursor);

        log.info("Cursor updated for {}/{}: lastItemDate={}, recentIds={}",
                platform, queryNormalized, cursor.getLastItemDate(), recentIds.size());
    }

    private Set<String> deserializeRecentIds(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return new LinkedHashSet<>();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {
            });
            return new LinkedHashSet<>(list);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize recentIds: {}", e.getMessage());
            return new LinkedHashSet<>();
        }
    }

    private String serializeRecentIds(Set<String> ids) {
        try {
            return objectMapper.writeValueAsString(ids);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize recentIds: {}", e.getMessage());
            return "[]";
        }
    }
}
