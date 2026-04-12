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

}
