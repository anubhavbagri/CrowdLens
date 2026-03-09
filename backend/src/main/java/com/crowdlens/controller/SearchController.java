package com.crowdlens.controller;

import com.crowdlens.model.dto.SearchRequest;
import com.crowdlens.model.dto.SearchResponse;
import com.crowdlens.service.SearchOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = { "http://localhost:3000", "http://localhost:3001" })
@Tag(name = "Search", description = "Search and analyze social media opinions")
public class SearchController {

    private final SearchOrchestrator orchestrator;

    @Operation(summary = "Analyze crowd opinions", description = "Searches Reddit for posts matching the query, runs AI analysis, and returns structured insights "
            +
            "including overall score, category breakdowns, curated testimonials, and persona matching.")
    @ApiResponse(responseCode = "200", description = "Analysis completed successfully", content = @Content(schema = @Schema(implementation = SearchResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid request (blank or too long query)")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(schema = @Schema(implementation = SearchRequest.class), examples = @ExampleObject(name = "Example search", value = "{\"query\": \"creatine supplement\", \"limit\": 10, \"maxComments\": 50}")))
    @PostMapping("/search")
    public ResponseEntity<SearchResponse> search(@Valid @RequestBody SearchRequest request) {
        log.info("POST /api/search — query: '{}'", request.query());
        SearchResponse response = orchestrator.executeSearch(request);
        return ResponseEntity.ok(response);
    }
}
