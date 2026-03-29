package com.crowdlens.service;

import com.crowdlens.event.SearchJobCreatedEvent;
import com.crowdlens.model.dto.SearchResponse;
import com.crowdlens.model.dto.SocialPostDto;
import com.crowdlens.model.entity.SearchJob;
import com.crowdlens.model.entity.SearchResult;
import com.crowdlens.model.entity.SocialPost;
import com.crowdlens.provider.PlatformRegistry;
import com.crowdlens.repository.SearchJobRepository;
import com.crowdlens.repository.SearchResultRepository;
import com.crowdlens.repository.SocialPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;

/**
 * Processes search jobs sequentially, one at a time.
 *
 * Triggered by SearchJobCreatedEvent after the job row commits to the DB.
 * Runs the full pipeline (scrape → AI → persist → cache) synchronously on the
 * calling thread — no thread pool, no CompletableFuture, minimal footprint.
 *
 * On startup, all PENDING/IN_PROGRESS jobs from a previous run are failed
 * immediately so the client can decide whether to resubmit them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchJobListener {

    private static final String RESTART_FAILURE_MESSAGE =
            "Server restarted before this job could complete. Please resubmit.";

    private final SearchJobRepository searchJobRepo;
    private final SearchResultRepository searchResultRepo;
    private final SocialPostRepository socialPostRepo;
    private final PlatformRegistry platformRegistry;
    private final AIAnalysisEngine aiEngine;
    private final CacheService cacheService;

    // -------------------------------------------------------------------------
    // Startup: fail all incomplete jobs from the previous run
    // -------------------------------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failIncompleteJobsOnStartup() {
        int count = searchJobRepo.failAllIncomplete(RESTART_FAILURE_MESSAGE);
        if (count > 0) {
            log.warn("Server restart detected — failed {} incomplete job(s). Clients should resubmit.", count);
        }
    }

    // -------------------------------------------------------------------------
    // Job processing: runs after the PENDING job row is committed
    // -------------------------------------------------------------------------

    /**
     * AFTER_COMMIT ensures the SearchJob row is visible in the DB before we
     * start processing, avoiding a race where the listener reads a row that
     * the outer transaction hasn't flushed yet.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onJobCreated(SearchJobCreatedEvent event) {
        SearchJob job = searchJobRepo.findById(event.getJobId()).orElse(null);
        if (job == null) {
            log.error("SearchJob {} not found — skipping", event.getJobId());
            return;
        }
        processJob(job);
    }

    // -------------------------------------------------------------------------
    // Pipeline
    // -------------------------------------------------------------------------

    @Transactional
    public void processJob(SearchJob job) {
        log.info("Processing job {} — query: '{}'", job.getId(), job.getQuery());

        // Mark IN_PROGRESS
        job.setStatus(SearchJob.Status.IN_PROGRESS);
        searchJobRepo.save(job);

        try {
            // 1. Search all platforms (sequential, blocking)
            List<SocialPostDto> posts = platformRegistry.searchAll(
                    job.getQuery(), job.getLimit(), job.getMaxComments());

            log.info("Job {} — collected {} posts from {} platform(s)",
                    job.getId(), posts.size(), platformRegistry.getEnabledPlatforms().size());

            // 2. AI analysis
            log.info("Job {} — sending {} posts to AI", job.getId(), posts.size());
            AIAnalysisEngine.AnalysisResult analysis = aiEngine.analyze(posts, job.getQuery());

            // 3. Persist SearchResult
            SearchResult searchResult = SearchResult.builder()
                    .query(job.getQuery())
                    .queryNormalized(job.getQueryNormalized())
                    .overallScore(analysis.overallScore())
                    .overallVerdict(analysis.overallVerdict())
                    .analysis(analysis.rawJson())
                    .sourcePlatforms(platformRegistry.getEnabledPlatforms().toArray(new String[0]))
                    .postCount(posts.size())
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(7 * 24 * 3600))
                    .build();

            searchResult = searchResultRepo.save(searchResult);

            // 4. Persist SocialPosts (deduplicated)
            SearchResult finalResult = searchResult;
            List<SocialPost> socialPosts = posts.stream()
                    .filter(dto -> !socialPostRepo.existsByPlatformId(dto.platformId()))
                    .map(dto -> SocialPost.builder()
                            .platform(dto.platform())
                            .platformId(dto.platformId())
                            .searchResult(finalResult)
                            .source(dto.source())
                            .title(dto.title())
                            .body(dto.body())
                            .score(dto.score())
                            .permalink(dto.permalink())
                            .postedAt(dto.postedAt())
                            .build())
                    .toList();

            if (socialPosts.size() < posts.size()) {
                log.info("Job {} — skipped {} duplicate posts, saving {} new",
                        job.getId(), posts.size() - socialPosts.size(), socialPosts.size());
            }
            socialPostRepo.saveAll(socialPosts);

            // 5. Cache (only if AI succeeded)
            if (!"AI Unavailable".equals(analysis.overallVerdict())) {
                SearchResponse response = buildResponse(job, analysis, searchResult, posts.size());
                cacheService.put(job.getQueryNormalized(), cacheService.serialize(response));
            } else {
                log.warn("Job {} — skipping cache, AI analysis failed", job.getId());
            }

            // 6. Mark COMPLETED
            job.setStatus(SearchJob.Status.COMPLETED);
            job.setSearchResultId(searchResult.getId());
            searchJobRepo.save(job);

            log.info("Job {} COMPLETED — score={}, verdict={}, posts={}",
                    job.getId(), analysis.overallScore(), analysis.overallVerdict(), posts.size());

        } catch (Exception e) {
            log.error("Job {} FAILED: {}", job.getId(), e.getMessage(), e);
            job.setStatus(SearchJob.Status.FAILED);
            job.setErrorMessage(e.getMessage());
            searchJobRepo.save(job);
        }
    }

    // -------------------------------------------------------------------------
    // Response builder (mirrors SearchOrchestrator.buildResponse)
    // -------------------------------------------------------------------------

    private SearchResponse buildResponse(SearchJob job,
                                         AIAnalysisEngine.AnalysisResult analysis,
                                         SearchResult searchResult,
                                         int postCount) {
        return SearchResponse.builder()
                .id(searchResult.getId())
                .query(job.getQuery())
                .overallScore(analysis.overallScore())
                .overallVerdict(analysis.overallVerdict())
                .verdictSummary(analysis.verdictSummary())
                .categories(analysis.categories())
                .testimonials(analysis.testimonials())
                .personaAnalysis(analysis.personaAnalysis())
                .postCount(postCount)
                .sourcePlatforms(platformRegistry.getEnabledPlatforms())
                .analyzedAt(Instant.now())
                .cached(false)
                .build();
    }
}
