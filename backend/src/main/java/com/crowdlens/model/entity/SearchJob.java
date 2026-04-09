package com.crowdlens.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "search_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchJob {

    public enum Status { PENDING, IN_PROGRESS, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(name = "query_normalized", nullable = false, columnDefinition = "TEXT")
    private String queryNormalized;

    // "post_limit" avoids collision with SQL reserved keyword LIMIT
    @Column(name = "post_limit", nullable = false)
    private int limit;

    @Column(name = "max_comments", nullable = false)
    private int maxComments;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "search_result_id")
    private UUID searchResultId;

    // Stores serialized SearchResponse JSON on COMPLETED, so polling doesn't
    // depend on DynamoDB availability or TTL expiry.
    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
