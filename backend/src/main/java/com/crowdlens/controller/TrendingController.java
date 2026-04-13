package com.crowdlens.controller;

import com.crowdlens.model.dto.TrendingResponse;
import com.crowdlens.service.TrendingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "Trending", description = "Discovery content for the landing page")
public class TrendingController {

    private final TrendingService trendingService;

    @Operation(
        summary = "Get trending, recent, and popular searches",
        description = "Returns three sections of discovery data for the landing page: " +
                      "trending (most frequently searched), recent (latest completed analyses), " +
                      "and popular categories. Merges real data with curated fallbacks to avoid empty states.")
    @ApiResponse(responseCode = "200", description = "Discovery data",
            content = @Content(schema = @Schema(implementation = TrendingResponse.class)))
    @GetMapping("/trending")
    public ResponseEntity<TrendingResponse> getTrending() {
        log.debug("GET /api/trending");
        return ResponseEntity.ok(trendingService.getTrending());
    }
}
