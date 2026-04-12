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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Processes search jobs from the rqueue "search-jobs" queue one at a time.
 * concurrency="1" ensures sequential execution — no parallel jobs on this low-powered server.
 *
 * Data split:
 *  - SQLite (SearchResult): lightweight permanent index — query, category, subcategory, score only
 *  - DynamoDB: full AI-generated JSON (verdict, metrics, positives, snippets etc.)
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
    private final ImageResolutionService imageResolutionService;
    private final TransactionTemplate txTemplate;

    @RqueueListener(value = "search-jobs", concurrency = "1", numRetries = "0")
    public void onMessage(SearchJobMessage message) {
        SearchJob job = searchJobRepo.findById(message.jobId()).orElse(null);
        if (job == null) {
            log.error("SearchJob {} not found in DB — discarding message", message.jobId());
            return;
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
            // 1. Scrape all platforms
            List<SocialPostDto> posts = platformRegistry.searchAll(
                    job.getQuery(), job.getLimit(), job.getMaxComments());
            log.info("Job {} — {} posts collected", job.getId(), posts.size());

            // 2. AI analysis (includes competitor suggestions in response)
            AIAnalysisEngine.AnalysisResult analysis = aiEngine.analyze(posts, job.getQuery());

            // 3. Resolve product image: Reddit (AI-validated) → Amazon fallback → null
            String resolvedImageUrl = analysis.productImageUrl();
            if (resolvedImageUrl == null || resolvedImageUrl.isBlank()) {
                log.info("Job {} — no Reddit image from AI, trying Amazon fallback", job.getId());
                resolvedImageUrl = imageResolutionService.fetchFromAmazon(job.getQuery()).orElse(null);
            } else {
                log.info("Job {} — using AI-validated Reddit image: {}", job.getId(), resolvedImageUrl);
            }

            // 4. Encode to base64 for reliable share card rendering (CORS-safe)
            final String finalImageUrl = resolvedImageUrl;
            final String imageBase64 = resolvedImageUrl != null
                    ? imageResolutionService.toBase64DataUri(resolvedImageUrl).orElse(null)
                    : null;

            if (imageBase64 != null) {
                log.info("Job {} — image base64 encoded successfully", job.getId());
            } else if (resolvedImageUrl != null) {
                log.warn("Job {} — image URL found but base64 encoding failed, URL will still be stored", job.getId());
            }

            txTemplate.executeWithoutResult(status -> {
                // 5. Save lean SearchResult to SQLite — query index + score + image URL only
                SearchResult searchResult = searchResultRepo.save(SearchResult.builder()
                        .query(job.getQuery())
                        .queryNormalized(job.getQueryNormalized())
                        .overallScore(analysis.overallScore())
                        .productCategory(analysis.productCategory())
                        .productSubCategory(analysis.productSubCategory())
                        .verdictSentence(analysis.verdictSentence())
                        .sourcePlatforms(String.join(",", platformRegistry.getEnabledPlatforms()))
                        .postCount(posts.size())
                        .imageUrl(finalImageUrl)
                        .createdAt(Instant.now())
                        .build());

                // 4. Persist SocialPosts (deduplicated by platformId)
                List<SocialPost> socialPosts = posts.stream()
                        .filter(dto -> !socialPostRepo.existsByPlatformId(dto.platformId()))
                        .map(dto -> SocialPost.builder()
                                .platform(dto.platform())
                                .platformId(dto.platformId())
                                .searchResult(searchResult)
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

                // 7. Full AI JSON → DynamoDB cache only (not SQLite)
                SearchResponse response = buildResponse(job, searchResult, analysis, posts.size(), finalImageUrl, imageBase64);
                boolean aiSucceeded = analysis.metrics() != null && !analysis.metrics().isEmpty();
                if (aiSucceeded) {
                    cacheService.put(job.getQueryNormalized(), cacheService.serialize(response));
                } else {
                    log.warn("Job {} — skipping DynamoDB cache, AI analysis produced no metrics", job.getId());
                }

                // 6. Seed competitor rows to SQLite if none exist for this subcategory yet
                seedCompetitorsIfNeeded(analysis, job.getQueryNormalized());

                // 7. Mark COMPLETED
                job.setStatus(SearchJob.Status.COMPLETED);
                job.setSearchResultId(searchResult.getId());
                job.setResultJson(cacheService.serialize(response));
                searchJobRepo.save(job);
            });

            log.info("Job {} COMPLETED", job.getId());

        } catch (Exception e) {
            log.error("Job {} FAILED: {}", job.getId(), e.getMessage(), e);
            txTemplate.executeWithoutResult(status -> {
                job.setStatus(SearchJob.Status.FAILED);
                job.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                searchJobRepo.save(job);
            });
        }
    }

    /**
     * Seeds AI-suggested competitors into SQLite if no existing competitors are found
     * for the detected subcategory. This ensures CompetitorCard always has data to show,
     * even before users search for those competitors directly.
     *
     * Seeds are PERMANENT entries — they act as placeholders until a real search replaces them.
     * A real search inserts a newer row; the competitor query picks MAX(createdAt) so
     * real scores always win over estimates.
     */
    private void seedCompetitorsIfNeeded(AIAnalysisEngine.AnalysisResult analysis,
                                          String currentQueryNormalized) {
        if (analysis.productSubCategory() == null || analysis.competitorSeeds().isEmpty()) {
            return;
        }

        List<SearchResult> existing = searchResultRepo.findCompetitors(
                analysis.productCategory(), analysis.productSubCategory(), currentQueryNormalized);

        if (!existing.isEmpty()) {
            log.debug("Competitors already exist for subcategory '{}' — skipping seed",
                    analysis.productSubCategory());
            return;
        }

        log.info("No competitors found for subcategory '{}' — seeding {} AI-suggested competitors",
                analysis.productSubCategory(), analysis.competitorSeeds().size());

        for (AIAnalysisEngine.AnalysisResult.CompetitorSeed seed : analysis.competitorSeeds()) {
            String normalizedName = normalize(seed.name());

            // Skip if this product is already in the index (from a previous search)
            if (searchResultRepo.findTopByQueryNormalizedOrderByCreatedAtDesc(normalizedName).isPresent()) {
                log.debug("Competitor '{}' already indexed — skipping", seed.name());
                continue;
            }

            searchResultRepo.save(SearchResult.builder()
                    .query(seed.name())
                    .queryNormalized(normalizedName)
                    .overallScore(seed.estimatedScore())
                    .productCategory(analysis.productCategory())
                    .productSubCategory(analysis.productSubCategory())
                    .createdAt(Instant.now())
                    .build());

            log.info("Seeded competitor '{}' with AI-estimated score {} [{}]",
                    seed.name(), seed.estimatedScore(), analysis.productSubCategory());
        }
    }

    private SearchResponse buildResponse(SearchJob job, SearchResult sr,
                                          AIAnalysisEngine.AnalysisResult analysis, int postCount,
                                          String imageUrl, String imageBase64) {
        return SearchResponse.builder()
                .id(sr.getId())
                .query(job.getQuery())
                .productCategory(analysis.productCategory())
                .productSubCategory(analysis.productSubCategory())
                .overallScore(analysis.overallScore())
                .verdictSentence(analysis.verdictSentence())
                .metrics(analysis.metrics())
                .positives(analysis.positives())
                .complaints(analysis.complaints())
                .bestFor(analysis.bestFor())
                .avoid(analysis.avoid())
                .evidenceSnippets(analysis.evidenceSnippets())
                .postCount(postCount)
                .sourcePlatforms(platformRegistry.getEnabledPlatforms())
                .analyzedAt(Instant.now())
                .cached(false)
                .productImageUrl(imageUrl)
                .productImageBase64(imageBase64)
                .build();
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
