package com.crowdlens.repository;

import com.crowdlens.model.entity.SearchJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SearchJobRepository extends JpaRepository<SearchJob, UUID> {

    /**
     * Bulk-marks all PENDING and IN_PROGRESS jobs as FAILED.
     * Called on startup to clean up jobs left unfinished by a prior crash or restart.
     */
    @Modifying
    @Query("UPDATE SearchJob j " +
           "SET j.status = com.crowdlens.model.entity.SearchJob.Status.FAILED, " +
           "    j.errorMessage = :message, " +
           "    j.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE j.status = com.crowdlens.model.entity.SearchJob.Status.PENDING " +
           "   OR j.status = com.crowdlens.model.entity.SearchJob.Status.IN_PROGRESS")
    int failAllIncomplete(String message);
}
