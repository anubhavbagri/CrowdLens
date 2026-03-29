package com.crowdlens.repository;

import com.crowdlens.model.entity.SearchJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SearchJobRepository extends JpaRepository<SearchJob, UUID> {

    /**
     * Finds all jobs in PENDING or IN_PROGRESS state.
     * Used on startup to fail any jobs that were interrupted by a server restart.
     */
    @Query("SELECT j FROM SearchJob j WHERE j.status = com.crowdlens.model.entity.SearchJob.Status.PENDING " +
           "OR j.status = com.crowdlens.model.entity.SearchJob.Status.IN_PROGRESS")
    List<SearchJob> findAllIncomplete();

    /**
     * Bulk-fails all incomplete jobs in a single update.
     */
    @Modifying
    @Query("UPDATE SearchJob j SET j.status = com.crowdlens.model.entity.SearchJob.Status.FAILED, " +
           "j.errorMessage = :message, j.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE j.status = com.crowdlens.model.entity.SearchJob.Status.PENDING " +
           "OR j.status = com.crowdlens.model.entity.SearchJob.Status.IN_PROGRESS")
    int failAllIncomplete(String message);
}
