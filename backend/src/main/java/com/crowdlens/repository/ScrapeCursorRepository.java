package com.crowdlens.repository;

import com.crowdlens.model.entity.ScrapeCursor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ScrapeCursorRepository extends JpaRepository<ScrapeCursor, UUID> {

    Optional<ScrapeCursor> findByPlatformAndQueryNormalized(String platform, String queryNormalized);
}
