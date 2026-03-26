package com.crowdlens.service;

import com.crowdlens.model.dto.SearchRequest;
import com.crowdlens.model.dto.SearchResponse;
import com.crowdlens.model.dto.SocialPostDto;
import com.crowdlens.model.entity.SearchResult;
import com.crowdlens.model.entity.SocialPost;
import com.crowdlens.provider.PlatformRegistry;
import com.crowdlens.repository.SearchResultRepository;
import com.crowdlens.repository.SocialPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Central orchestrator for the search pipeline.
 * Coordinates: Cache → Platform search → AI analysis → Persist → Return.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchOrchestrator {

        private final PlatformRegistry platformRegistry;
        private final AIAnalysisEngine aiEngine;
        private final CacheService cacheService;
        private final SearchResultRepository searchResultRepo;
        private final SocialPostRepository socialPostRepo;

        /**
         * Executes the full search pipeline.
         */
        @Transactional
        public SearchResponse executeSearch(SearchRequest request) {
                String query = request.query();
                String normalizedQuery = normalizeQuery(query);
                log.info("Executing search for query: '{}' (normalized: '{}')", query, normalizedQuery);

                // 1. Check cache
                Optional<String> cached = cacheService.get(normalizedQuery);
                if (cached.isPresent()) {
                        log.info("Returning cached result for query: '{}'", query);
                        SearchResponse cachedResponse = cacheService.deserialize(cached.get(), SearchResponse.class);
                        // Return with cached flag
                        return SearchResponse.builder()
                                        .id(cachedResponse.id())
                                        .query(cachedResponse.query())
                                        .overallScore(cachedResponse.overallScore())
                                        .overallVerdict(cachedResponse.overallVerdict())
                                        .verdictSummary(cachedResponse.verdictSummary())
                                        .categories(cachedResponse.categories())
                                        .testimonials(cachedResponse.testimonials())
                                        .personaAnalysis(cachedResponse.personaAnalysis())
                                        .postCount(cachedResponse.postCount())
                                        .sourcePlatforms(cachedResponse.sourcePlatforms())
                                        .analyzedAt(cachedResponse.analyzedAt())
                                        .cached(true)
                                        .build();
                }

                // 2. Search all platforms
                int postLimit = request.effectiveLimit();
                int maxComments = request.effectiveMaxComments();
                List<SocialPostDto> posts = platformRegistry.searchAll(query, postLimit, maxComments);
                log.info("Using post limit: {}, max comments: {} (user-specified: {})",
                                postLimit, maxComments, request.limit() != null);
                log.info("Collected {} posts from {} platforms", posts.size(),
                                platformRegistry.getEnabledPlatforms().size());

                if (posts.isEmpty()) {
                        log.warn("No posts found for query: '{}'", query);
                        return buildEmptyResponse(query);
                }

                // 3. AI analysis
                log.info("━━━ Sending {} posts+comments to AI for analysis ━━━", posts.size());
                AIAnalysisEngine.AnalysisResult analysis = aiEngine.analyze(posts, query);

                // 4. Persist to database
                SearchResult searchResult = SearchResult.builder()
                                .query(query)
                                .queryNormalized(normalizedQuery)
                                .overallScore(analysis.overallScore())
                                .overallVerdict(analysis.overallVerdict())
                                .analysis(analysis.rawJson())
                                .sourcePlatforms(platformRegistry.getEnabledPlatforms().toArray(new String[0]))
                                .postCount(posts.size())
                                .createdAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(7 * 24 * 3600))
                                .build();

                searchResult = searchResultRepo.save(searchResult);

                // 4. Persist to database asynchronously (non-blocking — return response faster)
                SearchResult finalSearchResult = searchResult;
                List<SocialPost> socialPosts = posts.stream()
                                .filter(dto -> !socialPostRepo.existsByPlatformId(dto.platformId()))
                                .map(dto -> SocialPost.builder()
                                                .platform(dto.platform())
                                                .platformId(dto.platformId())
                                                .searchResult(finalSearchResult)
                                                .source(dto.source())
                                                .title(dto.title())
                                                .body(dto.body())
                                                .score(dto.score())
                                                .permalink(dto.permalink())
                                                .postedAt(dto.postedAt())
                                                .build())
                                .toList();
                if (socialPosts.size() < posts.size()) {
                        log.info("Skipped {} duplicate posts (already in DB), saving {} new",
                                        posts.size() - socialPosts.size(), socialPosts.size());
                }
                CompletableFuture.runAsync(() -> socialPostRepo.saveAll(socialPosts))
                        .exceptionally(ex -> {
                            log.warn("Async DB persist failed: {}", ex.getMessage());
                            return null;
                        });

                // 5. Build response
                SearchResponse response = SearchResponse.builder()
                                .id(searchResult.getId())
                                .query(query)
                                .overallScore(analysis.overallScore())
                                .overallVerdict(analysis.overallVerdict())
                                .verdictSummary(analysis.verdictSummary())
                                .categories(analysis.categories())
                                .testimonials(analysis.testimonials())
                                .personaAnalysis(analysis.personaAnalysis())
                                .postCount(posts.size())
                                .sourcePlatforms(platformRegistry.getEnabledPlatforms())
                                .analyzedAt(Instant.now())
                                .cached(false)
                                .build();

                // 6. Only cache successful AI results (not failures)
                if (!"AI Unavailable".equals(analysis.overallVerdict())) {
                        cacheService.put(normalizedQuery, cacheService.serialize(response));
                } else {
                        log.warn("Skipping cache — AI analysis failed, retry will re-attempt");
                }

                log.info("Search completed for '{}': score={}, verdict={}, posts={}",
                                query, analysis.overallScore(), analysis.overallVerdict(), posts.size());

                return response;
        }

        private SearchResponse buildEmptyResponse(String query) {
                return SearchResponse.builder()
                                .query(query)
                                .overallScore(0)
                                .overallVerdict("No Data")
                                .verdictSummary("No social media posts found for this query. Try a different search term.")
                                .categories(List.of())
                                .testimonials(List.of())
                                .postCount(0)
                                .sourcePlatforms(platformRegistry.getEnabledPlatforms())
                                .analyzedAt(Instant.now())
                                .cached(false)
                                .build();
        }

        /**
         * Normalizes query for cache key consistency.
         * Lowercases, trims, collapses whitespace.
         */
        private String normalizeQuery(String query) {
                return query.trim()
                                .toLowerCase(Locale.ROOT)
                                .replaceAll("\\s+", " ");
        }
}
