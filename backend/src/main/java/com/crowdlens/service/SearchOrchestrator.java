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
                                        .productCategory(cachedResponse.productCategory())
                                        .productSubCategory(cachedResponse.productSubCategory())
                                        .overallScore(cachedResponse.overallScore())
                                        .verdictSentence(cachedResponse.verdictSentence())
                                        .metrics(cachedResponse.metrics())
                                        .positives(cachedResponse.positives())
                                        .complaints(cachedResponse.complaints())
                                        .bestFor(cachedResponse.bestFor())
                                        .avoid(cachedResponse.avoid())
                                        .evidenceSnippets(cachedResponse.evidenceSnippets())
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
                                .verdictSentence(analysis.verdictSentence())
                                .productCategory(analysis.productCategory())
                                .analysis(analysis.rawJson())
                                .sourcePlatforms(platformRegistry.getEnabledPlatforms().toArray(new String[0]))
                                .postCount(posts.size())
                                .createdAt(Instant.now())
                                .expiresAt(Instant.now().plusSeconds(7 * 24 * 3600))
                                .build();

                searchResult = searchResultRepo.save(searchResult);

                // Save social posts (skip duplicates that already exist in DB)
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
                socialPostRepo.saveAll(socialPosts);

                // 5. Build response
                SearchResponse response = SearchResponse.builder()
                                .id(searchResult.getId())
                                .query(query)
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
                                .postCount(posts.size())
                                .sourcePlatforms(platformRegistry.getEnabledPlatforms())
                                .analyzedAt(Instant.now())
                                .cached(false)
                                .build();

                // 6. Only cache successful AI results (not when AI failed)
                boolean aiSucceeded = analysis.metrics() != null && !analysis.metrics().isEmpty();
                if (aiSucceeded) {
                        cacheService.put(normalizedQuery, cacheService.serialize(response));
                } else {
                        log.warn("Skipping cache — AI analysis produced no metrics, retry will re-attempt");
                }

                log.info("Search completed for '{}': category='{}', score={}, metrics={}, posts={}",
                                query, analysis.productCategory(), analysis.overallScore(),
                                analysis.metrics().size(), posts.size());

                return response;
        }

        private SearchResponse buildEmptyResponse(String query) {
                return SearchResponse.builder()
                                .query(query)
                                .overallScore(0)
                                .verdictSentence("No social media posts found for this query. Try a different search term.")
                                .metrics(List.of())
                                .positives(List.of())
                                .complaints(List.of())
                                .bestFor(List.of())
                                .avoid(List.of())
                                .evidenceSnippets(List.of())
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
