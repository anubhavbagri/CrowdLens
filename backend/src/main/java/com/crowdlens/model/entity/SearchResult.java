package com.crowdlens.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight permanent index of every analysed product.
 *
 * SQLite holds ONLY:
 *   - What the user searched
 *   - What category/subcategory the AI assigned
 *   - The overall score (for competitor comparisons)
 *
 * All AI-generated content (verdict, metrics, positives, snippets etc.)
 * lives in DynamoDB, keyed by queryNormalized.
 *
 * Rows are NEVER expired or deleted — this is the permanent product index.
 */
@Entity
@Table(name = "search_results", indexes = {
        @Index(name = "idx_search_query_norm", columnList = "queryNormalized"),
        @Index(name = "idx_search_created",    columnList = "createdAt"),
        @Index(name = "idx_search_category",   columnList = "productCategory"),
        @Index(name = "idx_search_subcategory",columnList = "productSubCategory")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Original query as typed by the user. */
    @Column(nullable = false, length = 500)
    private String query;

    /** Normalized query — used as cache key and competitor exclusion. */
    @Column(nullable = false, length = 500)
    private String queryNormalized;

    /** 0–100 community score from AI analysis. */
    @Column
    private Integer overallScore;

    /** Broad category (e.g. "Electronics", "Grooming", "Skincare"). */
    @Column(length = 100)
    private String productCategory;

    /** Specific subcategory (e.g. "Smartphone", "Electric Trimmer", "Gel Moisturizer"). */
    @Column(length = 100)
    private String productSubCategory;

    /**
     * One-line AI verdict shown optionally in the competitor card.
     * Null for AI-seeded placeholder rows (no Reddit analysis done yet).
     */
    @Column(columnDefinition = "TEXT")
    private String verdictSentence;

    /**
     * Comma-separated platform list, e.g. "reddit" or "reddit,twitter".
     * Null for AI-seeded placeholder rows.
     */
    @Column(length = 300)
    private String sourcePlatforms;

    /**
     * Number of Reddit posts analysed. Null for AI-seeded placeholder rows.
     */
    @Column
    private Integer postCount;

    /**
     * Resolved product image URL (may come from Reddit post or Amazon scrape).
     * AI-validated for relevance. Null for AI-seeded placeholder rows.
     */
    @Column(length = 2048)
    private String imageUrl;

    /** When this result was persisted. Used for ordering competitors by recency. */
    @Column
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    private void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
