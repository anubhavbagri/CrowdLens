package com.crowdlens.service;

import com.crowdlens.event.SearchJobCreatedEvent;
import com.crowdlens.model.dto.SearchRequest;
import com.crowdlens.model.dto.SearchResponse;
import com.crowdlens.model.entity.SearchJob;
import com.crowdlens.repository.SearchJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for search requests.
 *
 * Two paths:
 *  - Cache hit  → deserialize and return the full SearchResponse immediately.
 *  - Cache miss → persist a PENDING SearchJob, publish SearchJobCreatedEvent,
 *                 return the job ID so the client can poll for the result.
 *
 * All pipeline work (scrape → AI → persist → cache) runs in SearchJobListener,
 * triggered by the event after this transaction commits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchOrchestrator {

    private final CacheService cacheService;
    private final SearchJobRepository searchJobRepo;
    private final ApplicationEventPublisher eventPublisher;

    // -------------------------------------------------------------------------
    // Submit (cache miss path) — returns jobId
    // -------------------------------------------------------------------------

    @Transactional
    public UUID submitSearch(SearchRequest request) {
        String normalizedQuery = normalizeQuery(request.query());

        SearchJob job = SearchJob.builder()
                .query(request.query())
                .queryNormalized(normalizedQuery)
                .limit(request.effectiveLimit())
                .maxComments(request.effectiveMaxComments())
                .status(SearchJob.Status.PENDING)
                .build();

        job = searchJobRepo.save(job);
        UUID jobId = job.getId();

        log.info("Created job {} for query: '{}'", jobId, request.query());

        // Published AFTER_COMMIT by TransactionalEventListener in SearchJobListener
        eventPublisher.publishEvent(new SearchJobCreatedEvent(this, jobId));

        return jobId;
    }

    // -------------------------------------------------------------------------
    // Cache fast-path — called by the controller before submitSearch
    // -------------------------------------------------------------------------

    public Optional<SearchResponse> getCachedResult(String query) {
        String normalizedQuery = normalizeQuery(query);
        return cacheService.get(normalizedQuery)
                .map(json -> {
                    log.info("Cache hit for query: '{}'", query);
                    SearchResponse cached = cacheService.deserialize(json, SearchResponse.class);
                    return SearchResponse.builder()
                            .id(cached.id())
                            .query(cached.query())
                            .overallScore(cached.overallScore())
                            .overallVerdict(cached.overallVerdict())
                            .verdictSummary(cached.verdictSummary())
                            .categories(cached.categories())
                            .testimonials(cached.testimonials())
                            .personaAnalysis(cached.personaAnalysis())
                            .postCount(cached.postCount())
                            .sourcePlatforms(cached.sourcePlatforms())
                            .analyzedAt(cached.analyzedAt())
                            .cached(true)
                            .build();
                });
    }

    // -------------------------------------------------------------------------
    // Job status / result retrieval — called by the polling endpoint
    // -------------------------------------------------------------------------

    public Optional<SearchJob> getJob(UUID jobId) {
        return searchJobRepo.findById(jobId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String normalizeQuery(String query) {
        return query.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
