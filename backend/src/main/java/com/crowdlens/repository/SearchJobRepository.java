package com.crowdlens.repository;

import com.crowdlens.model.entity.SearchJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SearchJobRepository extends JpaRepository<SearchJob, UUID> {

    /**
     * Bulk-marks all PENDING and IN_PROGRESS jobs as FAILED.
     * Called on startup to clean up jobs left unfinished by a prior crash or restart.
     * Uses native SQL because JPQL cannot resolve inner enum class literals in bulk UPDATE statements.
     */
    @Modifying
    @Query(value = "UPDATE search_jobs SET status = 'FAILED', error_message = :message, " +
                   "updated_at = CURRENT_TIMESTAMP " +
                   "WHERE status IN ('PENDING', 'IN_PROGRESS')",
           nativeQuery = true)
    int failAllIncomplete(@Param("message") String message);
}
