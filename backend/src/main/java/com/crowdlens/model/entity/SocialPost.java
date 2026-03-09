package com.crowdlens.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "social_posts", indexes = {
        @Index(name = "idx_posts_platform", columnList = "platform,platformId"),
        @Index(name = "idx_posts_search", columnList = "searchResult_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialPost {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 50)
    private String platform;

    @Column(nullable = false, length = 100, unique = true)
    private String platformId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "search_result_id")
    private SearchResult searchResult;

    @Column(length = 200)
    private String source;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column
    @Builder.Default
    private Integer score = 0;

    @Column(length = 500)
    private String permalink;

    @Column
    private Instant postedAt;

    @Column
    @Builder.Default
    private Instant scrapedAt = Instant.now();

    @PrePersist
    private void prePersist() {
        if (scrapedAt == null)
            scrapedAt = Instant.now();
    }
}
