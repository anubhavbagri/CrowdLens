package com.crowdlens.service;

import com.crowdlens.model.dto.SearchRequest;
import com.crowdlens.model.dto.SearchResponse;
import com.crowdlens.model.entity.SearchJob;
import com.crowdlens.repository.SearchJobRepository;
import com.github.sonus21.rqueue.core.RqueueMessageEnqueuer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates search job lifecycle: cache check, job creation, queue enqueue, and status lookup.
 * Actual pipeline execution (scrape → AI → persist → cache) is handled by SearchJobListener.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchOrchestrator {

    private static final String QUEUE = "search-jobs";

    private final CacheService cacheService;
    private final SearchJobRepository searchJobRepo;
    private final RqueueMessageEnqueuer rqueueMessageEnqueuer;

    /**
     * Cache fast-path: returns a cached SearchResponse (with cached=true) if DynamoDB has a hit.
     * Uses the new dynamic-metrics response shape (Phase 5).
     */
    public Optional<SearchResponse> getCachedResult(String query) {
        String normalizedQuery = normalizeQuery(query);

        // Tier 1: exact hash match
        Optional<String> cachedJson = cacheService.get(normalizedQuery);

        // Tier 2: on miss, fall back to Jaccard word-similarity scan
        if (cachedJson.isEmpty()) {
            cachedJson = cacheService.findSimilar(normalizedQuery);
        }

        return cachedJson.map(json -> {
            log.info("Cache HIT for query: '{}'", query);
            SearchResponse cached = cacheService.deserialize(json, SearchResponse.class);
            return SearchResponse.builder()
                    .id(cached.id())
                    .query(cached.query())
                    .productCategory(cached.productCategory())
                    .productSubCategory(cached.productSubCategory())
                    .overallScore(cached.overallScore())
                    .verdictSentence(cached.verdictSentence())
                    .metrics(cached.metrics())
                    .positives(cached.positives())
                    .complaints(cached.complaints())
                    .bestFor(cached.bestFor())
                    .avoid(cached.avoid())
                    .evidenceSnippets(cached.evidenceSnippets())
                    .postCount(cached.postCount())
                    .sourcePlatforms(cached.sourcePlatforms())
                    .analyzedAt(cached.analyzedAt())
                    .cached(true)
                    .build();
        });
    }

    /**
     * Persists a PENDING SearchJob to SQLite.
     * Must be @Transactional so the row commits to DB before enqueueJob() sends the Redis message.
     * This prevents a race where the listener dequeues the message before the row is visible.
     */
    @Transactional
    public UUID persistJob(SearchRequest request) {
        String normalizedQuery = normalizeQuery(request.query());
        SearchJob job = SearchJob.builder()
                .query(request.query())
                .queryNormalized(normalizedQuery)
                .limit(request.effectiveLimit())
                .maxComments(request.effectiveMaxComments())
                .status(SearchJob.Status.PENDING)
                .build();
        UUID jobId = searchJobRepo.save(job).getId();
        log.info("Persisted job {} for query: '{}'", jobId, request.query());
        return jobId;
    }

    /**
     * Publishes the job message to Redis via rqueue.
     * Called AFTER persistJob() returns (and its transaction has committed).
     */
    public void enqueueJob(UUID jobId) {
        rqueueMessageEnqueuer.enqueue(QUEUE, new SearchJobMessage(jobId));
        log.info("Enqueued job {} to queue '{}'", jobId, QUEUE);
    }

    /**
     * Looks up a job by ID for the polling endpoint.
     */
    public Optional<SearchJob> getJob(UUID jobId) {
        return searchJobRepo.findById(jobId);
    }

    /**
     * Deserializes the stored result from a COMPLETED job.
     * The result JSON is written by SearchJobListener on completion and is always available
     * regardless of DynamoDB TTL or availability.
     */
    public Optional<SearchResponse> getResultForJob(SearchJob job) {
        if (job.getResultJson() == null) return Optional.empty();
        return Optional.of(cacheService.deserialize(job.getResultJson(), SearchResponse.class));
    }

    private String normalizeQuery(String query) {
        return query.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
