package com.crowdlens.repository;

import com.crowdlens.model.entity.SocialPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SocialPostRepository extends JpaRepository<SocialPost, UUID> {

    List<SocialPost> findBySearchResultId(UUID searchResultId);

    boolean existsByPlatformId(String platformId);
}
