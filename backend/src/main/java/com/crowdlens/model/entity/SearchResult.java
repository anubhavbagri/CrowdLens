package com.crowdlens.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "search_results", indexes = {
        @Index(name = "idx_search_query_norm", columnList = "queryNormalized"),
        @Index(name = "idx_search_created", columnList = "createdAt")
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

    @Column(nullable = false, length = 500)
    private String query;

    @Column(nullable = false, length = 500)
    private String queryNormalized;

    @Column
    private Integer overallScore;

    @Column(columnDefinition = "TEXT")
    private String overallVerdict;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String analysis;

    @Column(columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] sourcePlatforms;

    @Column
    @Builder.Default
    private Integer postCount = 0;

    @Column
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column
    private Instant expiresAt;

    @OneToMany(mappedBy = "searchResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<SocialPost> socialPosts = new ArrayList<>();

    @OneToOne(mappedBy = "searchResult", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ScrapeCursor scrapeCursor;

    @PrePersist
    private void prePersist() {
        if (createdAt == null)
            createdAt = Instant.now();
        if (expiresAt == null)
            expiresAt = Instant.now().plusSeconds(7 * 24 * 3600); // 7 days
    }
}
