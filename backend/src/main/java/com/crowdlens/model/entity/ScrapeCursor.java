package com.crowdlens.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scrape_cursors", indexes = {
        @Index(name = "idx_cursor_lookup", columnList = "platform,queryNormalized")
}, uniqueConstraints = {
        @UniqueConstraint(columnNames = { "platform", "queryNormalized" })
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScrapeCursor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(nullable = false, length = 500)
    private String queryNormalized;

    @Column
    private Instant lastItemDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private String recentIds = "[]";

    @Column
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_result_id")
    private SearchResult searchResult;

    @PrePersist
    @PreUpdate
    private void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
