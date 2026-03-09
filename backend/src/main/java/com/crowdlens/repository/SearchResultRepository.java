package com.crowdlens.repository;

import com.crowdlens.model.entity.SearchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SearchResultRepository extends JpaRepository<SearchResult, UUID> {

    Optional<SearchResult> findTopByQueryNormalizedOrderByCreatedAtDesc(String queryNormalized);
}
