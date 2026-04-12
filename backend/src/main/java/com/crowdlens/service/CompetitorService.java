package com.crowdlens.service;

import com.crowdlens.model.dto.CompetitorDto;
import com.crowdlens.model.entity.SearchResult;
import com.crowdlens.repository.SearchResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Resolves competitors for a given product query.
 *
 * Flow:
 *  1. Try SQLite: findCompetitorsBySubCategory (specific match — e.g. "Smartphone")
 *  2. If empty: try SQLite: findCompetitorsByCategory (broad fallback — e.g. "Electronics")
 *  3. If still empty: ask AI for 3 competitor suggestions (lightweight prompt, no Reddit)
 *     → persist those to SQLite permanently
 *     → return them immediately so the UI doesn't show an empty card
 *
 * This means:
 *  - First search of a new category: AI fallback runs (~2-5s), then cached in SQLite forever
 *  - Subsequent searches in the same category: instant SQLite read
 *  - When a user later searches a seeded competitor: real Reddit score replaces the AI estimate
 *    (newer createdAt wins the MAX query)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitorService {

    private final SearchResultRepository searchResultRepo;
    private final AIAnalysisEngine aiEngine;

    @Transactional
    public List<CompetitorDto> getCompetitors(String category, String subCategory,
                                               String productQuery, int limit) {
        if (category == null || category.isBlank()) return List.of();

        String normalizedExclude = normalize(productQuery);

        // 1. Jaccard Similarity matches (fetch latest unique results first)
        List<SearchResult> allDbResults = searchResultRepo.findAllLatestUniqueResults(normalizedExclude);
        List<SearchResult> finalResults = new java.util.ArrayList<>(allDbResults.stream()
                .map(sr -> new Object() {
                    final SearchResult result = sr;
                    final double score = com.crowdlens.util.JaccardUtils.calculateSimilarity(normalizedExclude, sr.getQueryNormalized());
                })
                .filter(obj -> obj.score >= 0.3) // Basic similarity threshold
                .sorted(java.util.Comparator.comparingDouble(obj -> -obj.score)) // Descending score
                .map(obj -> obj.result)
                .limit(limit)
                .toList());

        if (!finalResults.isEmpty()) {
            log.info("SQLite Jaccard: Found {} similar product competitors for '{}'", finalResults.size(), productQuery);
        }

        // 2. SQLite Category + Subcategory matches (To pad any missing spots up to `limit`)
        if (finalResults.size() < limit && category != null && !category.isBlank()
                && subCategory != null && !subCategory.isBlank()) {
            
            List<SearchResult> categoryMatches = searchResultRepo.findCompetitors(category, subCategory, normalizedExclude);
            
            for (SearchResult match : categoryMatches) {
                if (finalResults.size() >= limit) break;
                // Prevent duplicating things we already got from Jaccard match
                boolean alreadyExists = finalResults.stream()
                        .anyMatch(r -> r.getQueryNormalized().equals(match.getQueryNormalized()));
                if (!alreadyExists) {
                    finalResults.add(match);
                }
            }
            if (!categoryMatches.isEmpty()) {
                log.info("SQLite Category: Added competitors for category='{}' subcategory='{}' (excluding '{}')",
                        category, subCategory, productQuery);
            }
        }

        // 3. AI fallback — fires on first encounter or when DB gives absolutely nothing
        if (finalResults.isEmpty()) {
            log.info("No competitors in SQLite for category='{}' subcategory='{}' — asking AI",
                    category, subCategory);
            finalResults = fetchAndSeedFromAI(productQuery, category, subCategory, normalizedExclude);
        }

        return finalResults.stream()
                .limit(limit)
                .map(sr -> new CompetitorDto(
                        sr.getQuery(),
                        sr.getOverallScore(),
                        sr.getVerdictSentence() != null,  // real = has verdict (from actual analysis)
                        sr.getVerdictSentence(),
                        sr.getSourcePlatforms(),
                        sr.getPostCount()
                ))
                .toList();
    }

    /**
     * Calls the AI for lightweight competitor suggestions (no Reddit scraping).
     * Persists each suggestion to SQLite as a permanent placeholder row.
     * Real scores will replace estimates when users actually search for those products.
     */
    private List<SearchResult> fetchAndSeedFromAI(String productQuery, String category,
                                                    String subCategory, String normalizedExclude) {
        List<AIAnalysisEngine.AnalysisResult.CompetitorSeed> seeds =
                aiEngine.suggestCompetitors(productQuery, category, subCategory);

        if (seeds.isEmpty()) {
            log.warn("AI returned no competitor suggestions for '{}'", productQuery);
            return List.of();
        }

        List<SearchResult> seeded = seeds.stream()
                .filter(seed -> {
                    String norm = normalize(seed.name());
                    // Don't seed the current product or already-indexed products
                    return !norm.equals(normalizedExclude) &&
                            searchResultRepo.findTopByQueryNormalizedOrderByCreatedAtDesc(norm).isEmpty();
                })
                .map(seed -> SearchResult.builder()
                        .query(seed.name())
                        .queryNormalized(normalize(seed.name()))
                        .overallScore(seed.estimatedScore())
                        .productCategory(category)
                        .productSubCategory(subCategory)
                        // verdictSentence, sourcePlatforms, postCount intentionally null
                        // — they'll be populated when user actually searches for this product
                        .createdAt(Instant.now())
                        .build())
                .toList();

        List<SearchResult> saved = searchResultRepo.saveAll(seeded);
        log.info("Seeded {} AI-suggested competitors for category='{}' subcategory='{}'",
                saved.size(), category, subCategory);
        return saved;
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
