package com.crowdlens.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent job record for a search request.
 * The HTTP layer returns the jobId immediately; a Spring event listener
 * processes the full pipeline (scrape → AI → persist → cache) synchronously
 * on the same thread, one job at a time.
 */
@Entity
@Table(name = "search_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchJob {

    public enum Status {
        PENDING, IN_PROGRESS, COMPLETED, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(name = "query_normalized", nullable = false, columnDefinition = "TEXT")
    private String queryNormalized;

    @Column(name = "post_limit", nullable = false)
    private int limit;

    @Column(name = "max_comments", nullable = false)
    private int maxComments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    /**
     * Populated once the job completes successfully — links to the persisted
     * SearchResult row so the polling endpoint can reconstruct the response.
     */
    @Column(name = "search_result_id")
    private UUID searchResultId;

    /**
     * Human-readable reason set when status = FAILED.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
