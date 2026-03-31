package com.crowdlens.service;

import com.crowdlens.model.dto.SearchResponse;
import com.crowdlens.model.dto.SocialPostDto;
import com.crowdlens.model.entity.SearchJob;
import com.crowdlens.model.entity.SearchResult;
import com.crowdlens.model.entity.SocialPost;
import com.crowdlens.provider.PlatformRegistry;
import com.crowdlens.repository.SearchJobRepository;
import com.crowdlens.repository.SearchResultRepository;
import com.crowdlens.repository.SocialPostRepository;
import com.github.sonus21.rqueue.annotation.RqueueListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Processes search jobs from the rqueue "search-jobs" queue one at a time.
 * concurrency="1" ensures sequential execution — no parallel jobs on this low-powered server.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchJobListener {

    private final SearchJobRepository searchJobRepo;
    private final SearchResultRepository searchResultRepo;
    private final SocialPostRepository socialPostRepo;
    private final PlatformRegistry platformRegistry;
    private final AIAnalysisEngine aiEngine;
    private final CacheService cacheService;

    /**
     * Dequeues and processes one search job at a time.
     *
     * concurrency="1"  → exactly one job processed at a time (critical for 2CPU/1GB server)
     * numRetries="0"   → do NOT retry; we handle all errors internally and mark FAILED
     */
    @RqueueListener(value = "search-jobs", concurrency = "1", numRetries = "0")
    @Transactional
    public void onMessage(SearchJobMessage message) {
        SearchJob job = searchJobRepo.findById(message.jobId()).orElse(null);
        if (job == null) {
            log.error("SearchJob {} not found in DB — discarding message", message.jobId());
            return; // return (not throw) to ACK the message; throwing triggers rqueue retry
        }
        if (job.getStatus() != SearchJob.Status.PENDING) {
            log.warn("Job {} is {} — expected PENDING, discarding duplicate message",
                    message.jobId(), job.getStatus());
            return;
        }
        processJob(job);
    }

    private void processJob(SearchJob job) {
        log.info("Starting job {} — query: '{}'", job.getId(), job.getQuery());

        job.setStatus(SearchJob.Status.IN_PROGRESS);
        searchJobRepo.save(job);

        try {
            // 1. Scrape all platforms (synchronous — no CompletableFuture)
            List<SocialPostDto> posts = platformRegistry.searchAll(
                    job.getQuery(), job.getLimit(), job.getMaxComments());
            log.info("Job {} — {} posts collected", job.getId(), posts.size());

            // 2. AI analysis
            AIAnalysisEngine.AnalysisResult analysis = aiEngine.analyze(posts, job.getQuery());

            // 3. Persist SearchResult to SQLite
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

            // 4. Persist SocialPosts to SQLite (synchronous, deduplicated by platformId)
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

            // 5. Build response and cache in DynamoDB (skip if AI failed)
            SearchResponse response = buildResponse(job, searchResult, analysis, posts.size());
            if (!"AI Unavailable".equals(analysis.overallVerdict())) {
                cacheService.put(job.getQueryNormalized(), cacheService.serialize(response));
            } else {
                log.warn("Job {} — skipping DynamoDB cache, AI analysis failed", job.getId());
            }

            // 6. Mark COMPLETED — store serialized result in job row for reliable polling
            job.setStatus(SearchJob.Status.COMPLETED);
            job.setSearchResultId(searchResult.getId());
            job.setResultJson(cacheService.serialize(response));
            searchJobRepo.save(job);

            log.info("Job {} COMPLETED — score={}, verdict='{}', posts={}",
                    job.getId(), analysis.overallScore(), analysis.overallVerdict(), posts.size());

        } catch (Exception e) {
            log.error("Job {} FAILED: {}", job.getId(), e.getMessage(), e);
            job.setStatus(SearchJob.Status.FAILED);
            job.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            searchJobRepo.save(job);
            // Do NOT rethrow — rqueue must ACK (remove) this message, not retry.
            // The job is marked FAILED in SQLite; the client will see it on next poll.
        }
    }

    private SearchResponse buildResponse(SearchJob job, SearchResult sr,
                                          AIAnalysisEngine.AnalysisResult analysis, int postCount) {
        return SearchResponse.builder()
                .id(sr.getId())
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
