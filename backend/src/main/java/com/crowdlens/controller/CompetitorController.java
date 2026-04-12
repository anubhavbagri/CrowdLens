package com.crowdlens.controller;

import com.crowdlens.model.dto.CompetitorDto;
import com.crowdlens.service.CompetitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Competitor context endpoint.
 *
 * GET /api/competitors?category=Electronics&subcategory=Smartphone&exclude=iPhone+15&limit=5
 *
 * Resolution: subcategory match first (e.g. "Smartphone"), category fallback (e.g. "Electronics").
 * Only returns products with fresh data (expiresAt > NOW()).
 */
@Slf4j
@RestController
@RequestMapping("/api/competitors")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CompetitorController {

    private final CompetitorService competitorService;

    @GetMapping
    public ResponseEntity<List<CompetitorDto>> getCompetitors(
            @RequestParam String category,
            @RequestParam(required = false) String subcategory,
            @RequestParam(required = false, defaultValue = "") String exclude,
            @RequestParam(required = false, defaultValue = "5") int limit) {

        log.info("GET /api/competitors — category='{}', subcategory='{}', exclude='{}', limit={}",
                category, subcategory, exclude, limit);

        List<CompetitorDto> competitors = competitorService.getCompetitors(
                category, subcategory, exclude, Math.min(limit, 8));

        return ResponseEntity.ok(competitors);
    }
}
