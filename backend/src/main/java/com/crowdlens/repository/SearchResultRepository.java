package com.crowdlens.repository;

import com.crowdlens.model.entity.SearchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SearchResultRepository extends JpaRepository<SearchResult, UUID> {

    Optional<SearchResult> findTopByQueryNormalizedOrderByCreatedAtDesc(String queryNormalized);

    /**
     * Finds competitor products matching BOTH productCategory AND productSubCategory.
     * e.g. category="Electronics" AND subCategory="Smartphone"
     * Returns the newest result per queryNormalized, excluding the current product.
     */
    @Query("""
        SELECT sr FROM SearchResult sr
        WHERE sr.productCategory = :category
          AND sr.productSubCategory = :subCategory
          AND sr.queryNormalized <> :excludeQuery
          AND sr.overallScore IS NOT NULL
          AND sr.createdAt = (
              SELECT MAX(sr2.createdAt) FROM SearchResult sr2
              WHERE sr2.queryNormalized = sr.queryNormalized
          )
        ORDER BY sr.createdAt DESC
    """)
    List<SearchResult> findCompetitors(String category, String subCategory, String excludeQuery);

    /**
     * Fetch the most recent SearchResult for each unique queryNormalized,
     * excluding the specified query. This provides a clean list of existing
     * products for in-memory Jaccard similarity scoring.
     */
    @Query("""
        SELECT sr FROM SearchResult sr
        WHERE sr.queryNormalized <> :excludeQuery
          AND sr.createdAt = (
              SELECT MAX(sr2.createdAt) FROM SearchResult sr2
              WHERE sr2.queryNormalized = sr.queryNormalized
          )
    """)
    List<SearchResult> findAllLatestUniqueResults(String excludeQuery);

    // ── Trending / Discovery Queries ──
    // Note on SQL injection safety:
    //   - findPopularCategories uses JPQL (ORM-managed, parameterized)
    //   - findTrendingByFrequency uses native SQL because it JOINs two unrelated JPA entities
    //     (SearchResult ↔ SearchJob have no @ManyToOne mapping). It takes ZERO user parameters,
    //     so SQL injection is not possible. Spring Data uses PreparedStatement regardless.

    /**
     * Most frequently searched products (ranked by job count, weighted by recency).
     * Native SQL required: JOINs search_results with search_jobs — no JPA relationship exists.
     * Takes zero user parameters.
     */
    @Query(value = """
        SELECT sr.query AS query, sr.overall_score AS score, sr.product_category AS category
        FROM search_results sr
        INNER JOIN search_jobs sj ON sj.query_normalized = sr.query_normalized
        WHERE sr.overall_score IS NOT NULL
          AND sj.status = 'COMPLETED'
          AND sr.created_at = (
              SELECT MAX(sr2.created_at) FROM search_results sr2
              WHERE sr2.query_normalized = sr.query_normalized
          )
        GROUP BY sr.query_normalized
        ORDER BY COUNT(sj.id) DESC, MAX(sj.created_at) DESC
        LIMIT 8
    """, nativeQuery = true)
    List<TrendingItemProjection> findTrendingByFrequency();

    /**
     * Top product categories by number of unique products searched.
     * Uses JPQL with constructor expression.
     */
    @Query("""
        SELECT sr.productCategory AS name, COUNT(DISTINCT sr.queryNormalized) AS searchCount
        FROM SearchResult sr
        WHERE sr.productCategory IS NOT NULL
          AND sr.overallScore IS NOT NULL
        GROUP BY sr.productCategory
        ORDER BY searchCount DESC
    """)
    List<CategoryProjection> findPopularCategories();

    /**
     * Fetch product query names for a given category.
     * Returns distinct original queries, ordered by most recent first.
     */
    @Query("""
        SELECT DISTINCT sr.query FROM SearchResult sr
        WHERE sr.productCategory = :category
          AND sr.overallScore IS NOT NULL
        ORDER BY sr.query ASC
    """)
    List<String> findProductsByCategory(String category);

    /** Projection for trending items (native SQL result mapping). */
    interface TrendingItemProjection {
        String getQuery();
        Integer getScore();
        String getCategory();
    }

    /** Projection for category counts. */
    interface CategoryProjection {
        String getName();
        long getSearchCount();
    }
}
